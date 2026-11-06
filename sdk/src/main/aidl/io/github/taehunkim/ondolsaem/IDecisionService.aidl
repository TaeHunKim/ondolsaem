package io.github.taehunkim.ondolsaem;

/** Category "decision" (API 3). */
interface IDecisionService {
    /**
     * For each hypothesis, the probability (0..1) that it follows from [premise] (natural language inference:
     * entailment against contradiction). Long premises are cut to the model's window.
     */
    float[] entail(String premise, in String[] hypotheses, int priority) = 1;
}
