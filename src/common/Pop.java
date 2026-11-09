package common;

/**
 * 사용자가 키우는 포프 한 마리의 상태.
 *   단계(알/아기/성체), 성장 경험치, 배고픔·청결·행복(0~100), 유전자
 *
 * 프로토콜로는 "/POP 단계 경험치 배고픔 청결 행복 유전자" 한 줄로 보낸다.
 *   예) /POP BABY 40 30 70 80 body=slime_body;eye=bean_eye;...;color=FFB3C1,...
 * 유전자에는 띄어쓰기가 없어서 맨 뒤에 그대로 붙인다.
 */
public class Pop {

    public enum Stage {
        EGG("알"), BABY("아기"), ADULT("성체");

        public final String label;

        Stage(String label) {
            this.label = label;
        }
    }

    public Stage stage = Stage.EGG;
    public int exp = 0;
    public int hunger = GrowthRule.START_STAT;   // 높을수록 배고픔
    public int clean = GrowthRule.START_STAT;    // 높을수록 깨끗함
    public int happy = GrowthRule.START_STAT;    // 높을수록 행복함
    public Gene gene;

    public Pop(Gene gene) {
        this.gene = gene;
    }

    /** "/POP" 뒤에 붙는 부분 */
    public String toProtocol() {
        return stage.name() + " " + exp + " " + hunger + " " + clean + " " + happy + " " + gene;
    }

    /** toProtocol() 로 만든 문자열을 다시 읽는다. 형식이 틀리면 null */
    public static Pop fromProtocol(String body) {
        String[] t = body.trim().split(" ", 6);
        if (t.length < 6) {
            return null;
        }
        try {
            Pop p = new Pop(Gene.parse(t[5]));
            p.stage = Stage.valueOf(t[0]);
            p.exp = Integer.parseInt(t[1]);
            p.hunger = Integer.parseInt(t[2]);
            p.clean = Integer.parseInt(t[3]);
            p.happy = Integer.parseInt(t[4]);
            return p;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 다음 단계까지 필요한 경험치 (성체는 0) */
    public int expGoal() {
        return GrowthRule.goal(stage);
    }
}
