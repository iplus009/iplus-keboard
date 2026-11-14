/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.futo.inputmethod.latin.utils;

import android.util.Log;

import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.futo.inputmethod.latin.define.DebugFlags;

import java.util.Locale;

public final class AutoCorrectionUtils {
    private static final boolean DBG = DebugFlags.DEBUG_ENABLED;
    private static final String TAG = AutoCorrectionUtils.class.getSimpleName();

    private AutoCorrectionUtils() {
        // Purely static class: can't instantiate.
    }

    private static String stripApostrophesAndHyphens(final String word) {
        final StringBuilder sb = new StringBuilder(word.length());
        for (int i = 0; i < word.length(); i++) {
            final char c = word.charAt(i);
            if (c != '\'' && c != '\u2019' && c != '-') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static int damerauLevenshteinDistance(final String source, final String target) {
        if (source == null || target == null) {
            return Integer.MAX_VALUE;
        }
        final int[] s = source.codePoints().toArray();
        final int[] t = target.codePoints().toArray();
        final int n = s.length;
        final int m = t.length;
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        final int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                final int cost = (s[i - 1] == t[j - 1]) ? 0 : 1;
                int best = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && s[i - 1] == t[j - 2] && s[i - 2] == t[j - 1]) {
                    best = Math.min(best, dp[i - 2][j - 2] + 1);
                }
                dp[i][j] = best;
            }
        }
        return dp[n][m];
    }

    /**
     * Prevents aggressive spacebar auto-corrections from replacing phonetic/Banglish words
     * (e.g. "kicu" -> "muchi") or short 1-2 character words ("ze", "ki").
     */
    public static boolean isPlausibleAutoCorrection(final String consideredWord,
            final String suggestionWord) {
        if (consideredWord == null || suggestionWord == null) {
            return false;
        }
        final String source = consideredWord.trim();
        final String target = suggestionWord.trim();
        if (source.isEmpty() || target.isEmpty()) {
            return false;
        }
        if (source.equalsIgnoreCase(target)) {
            return true;
        }
        final String strippedSource = stripApostrophesAndHyphens(source);
        final String strippedTarget = stripApostrophesAndHyphens(target);
        if (!strippedSource.isEmpty() && strippedSource.equalsIgnoreCase(strippedTarget)) {
            return true;
        }
        final String normalizedSource = source.toLowerCase(Locale.ROOT);
        final String normalizedTarget = target.toLowerCase(Locale.ROOT);
        final int sourceLength = normalizedSource.codePointCount(0, normalizedSource.length());
        if (sourceLength <= 2) {
            return false;
        }
        if (normalizedTarget.indexOf(' ') >= 0 && normalizedSource.indexOf(' ') < 0) {
            return false;
        }
        final int targetLength = normalizedTarget.codePointCount(0, normalizedTarget.length());
        final int maxAllowedDistance = (sourceLength <= 5) ? 1 : 2;
        if (Math.abs(sourceLength - targetLength) > maxAllowedDistance) {
            return false;
        }
        return damerauLevenshteinDistance(normalizedSource, normalizedTarget) <= maxAllowedDistance;
    }

    public static boolean suggestionExceedsThreshold(final SuggestedWordInfo suggestion,
            final String consideredWord, final float threshold) {
        if (null != suggestion) {
            if (!isPlausibleAutoCorrection(consideredWord, suggestion.mWord)) {
                return false;
            }
            // Shortlist a whitelisted word
            if (suggestion.isKindOf(SuggestedWordInfo.KIND_WHITELIST)) {
                return true;
            }
            // TODO: return suggestion.isAprapreateForAutoCorrection();
            if (!suggestion.isAprapreateForAutoCorrection()) {
                return false;
            }
            final int autoCorrectionSuggestionScore = suggestion.mScore;
            // TODO: when the normalized score of the first suggestion is nearly equals to
            //       the normalized score of the second suggestion, behave less aggressive.
            final float normalizedScore = BinaryDictionaryUtils.calcNormalizedScore(
                    consideredWord, suggestion.mWord, autoCorrectionSuggestionScore);
            if (DBG) {
                Log.d(TAG, "Normalized " + consideredWord + "," + suggestion + ","
                        + autoCorrectionSuggestionScore + ", " + normalizedScore
                        + "(" + threshold + ")");
            }
            if (normalizedScore >= threshold) {
                if (DBG) {
                    Log.d(TAG, "Exceeds threshold.");
                }
                return true;
            }
        }
        return false;
    }
}
