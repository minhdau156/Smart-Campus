#!/usr/bin/env bash
# Replay a read-model consumer (spec D5): rebuild its tables by reading its Kafka topics again
# from the start. Works on the local Docker Compose stack (infra/docker-compose.yml).
#
#   ./infra/replay.sh <consumer-group> <schema>.<table>[,<schema>.<table>...]
#   ./infra/replay.sh notification.probe notification.probe_read_model
#
# Steps:
#   1. look the consumer up in infra/consumer-registry.txt; refuse it if it is unknown or
#      non-replayable (a consumer that sends emails must never read old events again);
#   2. check the tables: only plain names, only in the consumer's own schema;
#   3. require the consumer to be stopped (its group has no members);
#   4. in ONE transaction: empty the listed tables and delete the consumer's processed_events rows;
#   5. reset the consumer group to the earliest offset of every topic it reads;
#   6. tell you to restart the app: the consumer then reads all events again and rebuilds the rows.
#
# Steps 4 and 5 can be run again safely if something fails in between.
set -euo pipefail
# Git Bash on Windows turns "/opt/kafka/..." into "C:/Program Files/Git/opt/..." before it reaches
# `docker compose exec`. This switches that off (no effect on Linux or macOS).
export MSYS_NO_PATHCONV=1

# `pwd -W` gives the Windows form (C:/...) in Git Bash, needed because path conversion is off;
# elsewhere it fails and plain `pwd` is used.
ROOT="$(cd "$(dirname "$0")/.." && (pwd -W 2>/dev/null || pwd))"
# The registry can be swapped for a test copy (used to check the refusals by hand).
REGISTRY="${CAMPUS_CONSUMER_REGISTRY:-$ROOT/infra/consumer-registry.txt}"
COMPOSE=(docker compose -f "$ROOT/infra/docker-compose.yml")
# Inside the Compose network the broker listens on kafka:29092 (localhost:29092 in its container).
KAFKA_GROUPS=(/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:29092)

die() {
  echo "replay: $*" >&2
  exit 1
}

[ $# -eq 2 ] || die "usage: $0 <consumer-group> <schema>.<table>[,<schema>.<table>...]"
GROUP="$1"
TABLES="$2"

# The group name ends up in SQL and in commands, so it must be a plain name.
[[ "$GROUP" =~ ^[A-Za-z0-9._-]+$ ]] || die "not a valid consumer group name: '$GROUP'"

# --- 1. registry ----------------------------------------------------------------------------
[ -f "$REGISTRY" ] || die "registry not found: $REGISTRY"
ENTRY="$(sed 's/#.*//' "$REGISTRY" | awk -v g="$GROUP" '$1 == g { print $2, $3 }')"
[ -n "$ENTRY" ] || die "'$GROUP' is not in $REGISTRY, so it cannot be replayed"
read -r SCHEMA REPLAY <<<"$ENTRY"
if [ "$REPLAY" != "replayable" ]; then
  die "REFUSED: '$GROUP' is $REPLAY (it has side effects outside the database, e.g. email or push)"
fi

# --- 2. tables ------------------------------------------------------------------------------
IFS=',' read -ra TABLE_LIST <<<"$TABLES"
[ ${#TABLE_LIST[@]} -gt 0 ] || die "no tables given"
for table in "${TABLE_LIST[@]}"; do
  [[ "$table" =~ ^[a-z_][a-z0-9_]*\.[a-z_][a-z0-9_]*$ ]] || die "not a <schema>.<table> name: '$table'"
  [ "${table%%.*}" = "$SCHEMA" ] || die "'$table' is not in $GROUP's schema '$SCHEMA'"
  case "${table#*.}" in
    processed_events | event_publication | flyway_schema_history)
      die "'$table' is shared bookkeeping, not a read model; it must not be emptied" ;;
  esac
done

# --- 3. the consumer must be stopped --------------------------------------------------------
# A running consumer would keep writing rows and would block the offset reset.
DESCRIBE="$("${COMPOSE[@]}" exec -T kafka "${KAFKA_GROUPS[@]}" --describe --group "$GROUP" --state 2>&1 || true)"
if grep -q "does not exist" <<<"$DESCRIBE"; then
  echo "replay: group '$GROUP' has no offsets yet; only the tables are cleared"
  GROUP_EXISTS=false
else
  # Header line, then: GROUP  COORDINATOR (ID)  ASSIGNMENT-STRATEGY  STATE  #MEMBERS
  MEMBERS="$(awk -v g="$GROUP" '$1 == g { print $NF }' <<<"$DESCRIBE")"
  [ -n "$MEMBERS" ] || die "could not read the state of group '$GROUP':"$'\n'"$DESCRIBE"
  [ "$MEMBERS" = "0" ] || die "'$GROUP' is still running ($MEMBERS member(s)). Stop the app first."
  GROUP_EXISTS=true
fi

# --- 4. clear the read model and the consumer's dedupe rows (one transaction) ----------------
echo "replay: emptying ${TABLE_LIST[*]} and $SCHEMA.processed_events rows of '$GROUP'"
"${COMPOSE[@]}" exec -T postgres sh -c 'psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<SQL
BEGIN;
TRUNCATE $(IFS=','; echo "${TABLE_LIST[*]}");
DELETE FROM $SCHEMA.processed_events WHERE consumer = '$GROUP';
COMMIT;
SQL

# --- 5. back to the first event -------------------------------------------------------------
if [ "$GROUP_EXISTS" = true ]; then
  echo "replay: resetting '$GROUP' to the earliest offset"
  "${COMPOSE[@]}" exec -T kafka "${KAFKA_GROUPS[@]}" --group "$GROUP" \
    --reset-offsets --to-earliest --all-topics --execute
fi

# --- 6. ---------------------------------------------------------------------------------------
echo "replay: done. Now restart the app: '$GROUP' will read every event again and rebuild the rows."
