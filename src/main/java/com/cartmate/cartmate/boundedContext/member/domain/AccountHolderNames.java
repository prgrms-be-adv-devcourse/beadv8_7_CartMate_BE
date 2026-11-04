package com.cartmate.cartmate.boundedContext.member.domain;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// 예금주명 비교용 정규화. 법인 형태는 지우지 않고 표기만 통일한다.
public final class AccountHolderNames {

    // 긴 표기부터 치환한다(유한책임회사가 유한회사보다 먼저).
    private static final Map<String, String> CORPORATE_FORMS = new LinkedHashMap<>();

    static {
        CORPORATE_FORMS.put("유한책임회사", "(유책)");
        CORPORATE_FORMS.put("주식회사", "(주)");
        CORPORATE_FORMS.put("유한회사", "(유)");
        CORPORATE_FORMS.put("합자회사", "(합자)");
        CORPORATE_FORMS.put("합명회사", "(합명)");
    }

    private AccountHolderNames() {
    }

    public static String normalize(String name) {
        if (name == null) return "";
        // NFKC로 ㈜ → (주), 전각 괄호·공백 → 반각으로 바뀐다.
        String result = Normalizer.normalize(name, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> form : CORPORATE_FORMS.entrySet()) {
            result = result.replace(form.getKey(), form.getValue());
        }
        return result;
    }

    // 정규화한 값이 완전히 같을 때만 true. 부분 일치와 법인 형태 위치 변경은 같게 보지 않는다.
    public static boolean matches(String a, String b) {
        String normalized = normalize(a);
        return !normalized.isEmpty() && normalized.equals(normalize(b));
    }
}
