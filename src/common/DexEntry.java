package common;

/**
 * 도감 한 칸: 청혼에 성공해서 떠나보낸 포프 (예민 담당).
 * 도감은 보기만 하고 다시 키울 수 없다. 청혼은 다 자란 포프만 하므로 도감의 포프는 모두 성체다.
 *
 * 프로토콜 한 칸 형식: 유전자|짝닉네임  (유전자와 닉네임에는 띄어쓰기와 | 가 없다)
 */
public class DexEntry {

    public final Gene gene;
    /** 청혼한(받은) 상대 이름 */
    public final String partner;

    public DexEntry(Gene gene, String partner) {
        this.gene = gene;
        this.partner = partner;
    }

    public String toProtocol() {
        return gene + "|" + partner;
    }

    /** "유전자|짝" -> DexEntry. 형식이 틀리면 null */
    public static DexEntry fromProtocol(String item) {
        int bar = item.lastIndexOf('|');
        if (bar <= 0 || bar == item.length() - 1) {
            return null;
        }
        return new DexEntry(Gene.parse(item.substring(0, bar)), item.substring(bar + 1));
    }

    /** 그림 그리기용 성체 포프 */
    public Pop toPop() {
        Pop p = new Pop(gene);
        p.stage = Pop.Stage.ADULT;
        return p;
    }
}
