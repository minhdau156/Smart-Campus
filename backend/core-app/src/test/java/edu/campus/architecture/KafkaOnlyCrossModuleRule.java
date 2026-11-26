package edu.campus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import edu.campus.kernel.event.DomainEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.context.event.EventListener;

/**
 * Spec D5: cross-module events are consumed only from Kafka, even between modules inside
 * core-app. An in-process listener ({@code @EventListener}, {@code @TransactionalEventListener},
 * {@code @ApplicationModuleListener}) may only handle events of its OWN module. Reason: an event
 * delivered in-process cannot be replayed; one read from Kafka can.
 *
 * <p>The event types of a listener are its parameter types and the {@code classes}/{@code value}
 * of its annotation. A {@code DomainEvent<X>} counts as event type {@code X}. A {@code DomainEvent}
 * whose payload type is not named ({@code DomainEvent<?>}) could carry any module's event, so it is
 * not allowed in a module either. Events from outside the modules (e.g. Spring's
 * {@code ApplicationReadyEvent}) are fine.
 *
 * <p>Modules are the packages directly below the root, as in {@link InternalPackageRule}. The two
 * shared libraries {@code kernel} and {@code starter} are not modules (same as platform-starter's
 * {@code CampusModuleDetection}), so this rule does not check them.
 */
final class KafkaOnlyCrossModuleRule {

    private static final Set<String> LIBRARIES = Set.of("kernel", "starter");

    private KafkaOnlyCrossModuleRule() {}

    static ArchRule inProcessListenersOnlyForOwnEvents(String root) {
        return methods()
                .that(areInProcessListenersInAModule(root))
                .should(onlyHandleOwnModulesEvents(root))
                .as("in-process event listeners should only handle their own module's events (spec D5)")
                .allowEmptyShould(true);
    }

    private static DescribedPredicate<JavaMethod> areInProcessListenersInAModule(String root) {
        return new DescribedPredicate<>("are in-process event listeners in a module") {
            @Override
            public boolean test(JavaMethod method) {
                String module =
                        InternalPackageRule.moduleOf(root, method.getOwner().getPackageName());
                boolean inModule = !module.isEmpty() && !LIBRARIES.contains(module);
                return inModule && isListener(method);
            }
        };
    }

    /** {@code @EventListener}, or an annotation built on it (transactional, application module). */
    private static boolean isListener(JavaMethod method) {
        return method.isAnnotatedWith(EventListener.class) || method.isMetaAnnotatedWith(EventListener.class);
    }

    private static ArchCondition<JavaMethod> onlyHandleOwnModulesEvents(String root) {
        return new ArchCondition<>("only handle their own module's events") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String ownModule =
                        InternalPackageRule.moduleOf(root, method.getOwner().getPackageName());
                for (JavaType eventType : eventTypesOf(method)) {
                    String problem = problemWith(root, ownModule, eventType);
                    if (problem != null) {
                        events.add(SimpleConditionEvent.violated(
                                method, "%s %s".formatted(method.getFullName(), problem)));
                    }
                }
            }
        };
    }

    /** The parameter types, plus classes named in the listener annotation. */
    private static List<JavaType> eventTypesOf(JavaMethod method) {
        List<JavaType> types = new ArrayList<>(method.getParameterTypes());
        for (JavaAnnotation<JavaMethod> annotation : method.getAnnotations()) {
            for (String attribute : List.of("classes", "value")) {
                annotation.get(attribute).ifPresent(value -> {
                    if (value instanceof JavaClass[] classes) {
                        types.addAll(List.of(classes));
                    }
                });
            }
        }
        return types;
    }

    /** Why this event type is not allowed for this module, or null if it is fine. */
    private static String problemWith(String root, String ownModule, JavaType eventType) {
        JavaClass rawType = eventType.toErasure();
        if (rawType.isEquivalentTo(DomainEvent.class)) {
            if (!(eventType instanceof JavaParameterizedType parameterized)
                    || !(parameterized.getActualTypeArguments().getFirst() instanceof JavaClass payload)) {
                return "handles a DomainEvent without a named payload type, which may be another module's event";
            }
            return problemWith(root, ownModule, payload);
        }
        String eventModule = InternalPackageRule.moduleOf(root, rawType.getPackageName());
        if (!eventModule.isEmpty() && !LIBRARIES.contains(eventModule) && !eventModule.equals(ownModule)) {
            return "handles %s of module '%s' in-process; consume it from Kafka instead"
                    .formatted(rawType.getSimpleName(), eventModule);
        }
        return null;
    }
}
