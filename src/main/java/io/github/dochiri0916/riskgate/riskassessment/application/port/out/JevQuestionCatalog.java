package io.github.dochiri0916.riskgate.riskassessment.application.port.out;

import java.util.Arrays;
import java.util.List;

public enum JevQuestionCatalog {
    SECURITY_RISK("security_risk",
            "Does this code change introduce or materially increase a plausible security vulnerability? "
                    + "Judge only a vulnerability plausibly introduced or materially increased by this change; "
                    + "a security-related file changing by itself is not evidence.",
            "A concrete changed behavior creates or materially increases an exploitable weakness that could "
                    + "compromise confidentiality, integrity, or availability.",
            "No plausible exploitable weakness is introduced or materially increased; security-related file "
                    + "movement or routine edits alone do not qualify."),
    AUTHORIZATION_RISK("authorization_risk",
            "Does this code change weaken an authentication or authorization boundary?",
            "The change removes or weakens an authorization check, tenant or user ownership validation, widens "
                    + "access to a protected resource, or weakens an authentication requirement.",
            "Authentication and authorization boundaries retain their prior effective scope and checks."),
    DATA_INTEGRITY_RISK("data_integrity_risk",
            "Could this code change cause persistent data loss, duplication, corruption, or inconsistency?",
            "A plausible changed execution path can lose, duplicate, corrupt, or leave persistent data inconsistent, "
                    + "for example through destructive migration or non-atomic writes.",
            "No meaningful persistent data integrity failure is plausibly introduced; data access code changing "
                    + "by itself does not qualify."),
    BREAKING_CHANGE("breaking_change",
            "Does this code change introduce backward-incompatible behavior for existing consumers?",
            "An existing external or internal caller relying on the prior supported contract can no longer compile, "
                    + "integrate, or observe compatible behavior because of this change.",
            "The change preserves existing consumer contracts; intentional internal implementation changes alone "
                    + "do not qualify.");

    private final String id;
    private final String prompt;
    private final String yesCriteria;
    private final String noCriteria;

    JevQuestionCatalog(
            final String id,
            final String instructions,
            final String trueCriteria,
            final String falseCriteria
    ) {
        this.id = id;
        this.prompt = instructions;
        this.yesCriteria = trueCriteria;
        this.noCriteria = falseCriteria;
    }

    public String key() { return id; }
    public String instructions() { return prompt; }
    public String trueCriteria() { return yesCriteria; }
    public String falseCriteria() { return noCriteria; }

    public static List<JevQuestionCatalog> questions() {
        return Arrays.asList(values());
    }
}
