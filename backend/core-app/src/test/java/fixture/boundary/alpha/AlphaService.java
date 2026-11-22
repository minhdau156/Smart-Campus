package fixture.boundary.alpha;

import fixture.boundary.beta.BetaApi;
import fixture.boundary.beta.internal.BetaSecret;

/**
 * TEST FIXTURE, on purpose WRONG: module "alpha" uses {@link BetaSecret} from module "beta"'s
 * {@code internal} package. BoundaryViolationTest proves that both Modulith and our ArchUnit rule
 * catch this (spec P3). Do not "fix" it.
 */
public class AlphaService {

    /** Allowed: BetaApi is in beta's root package, which is beta's public API. */
    public String allowed() {
        return new BetaApi().hello();
    }

    /** NOT allowed: reaches into beta.internal. */
    public String violation() {
        return new BetaSecret().secret();
    }
}
