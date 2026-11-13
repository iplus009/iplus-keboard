/*
 * Copyright (C) 2026 iPlus Keyboard
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

package org.futo.inputmethod.latin.inputlogic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.futo.inputmethod.event.Event;
import org.futo.inputmethod.keyboard.Keyboard;
import org.futo.inputmethod.keyboard.ProximityInfo;
import org.futo.inputmethod.latin.DictionaryFacilitator;
import org.futo.inputmethod.latin.LatinIMELegacy;
import org.futo.inputmethod.latin.NgramContext;
import org.futo.inputmethod.latin.Suggest.OnGetSuggestedWordsCallback;
import org.futo.inputmethod.latin.SuggestedWords;
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.futo.inputmethod.latin.common.ComposedData;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.settings.SettingsValues;
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion;
import org.futo.inputmethod.latin.suggestions.SuggestionStripViewAccessor;
import org.futo.inputmethod.latin.utils.SuggestionResults;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Locale;

/**
 * Instrumentation unit tests verifying Avro composing queries the loaded Bangla dictionary
 * and appends unique suggestions after phonetic candidates without crashing when unloaded.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AvroDictionarySuggestionTests {
    private LatinIMELegacy mLatinIMELegacy;
    private SuggestionStripViewAccessor mSuggestionStripViewAccessor;
    private DictionaryFacilitator mDictionaryFacilitator;
    private Keyboard mKeyboard;
    private SettingsValues mSettingsValues;
    private InputLogic mInputLogic;

    @Before
    public void setUp() {
        mLatinIMELegacy = mock(LatinIMELegacy.class);
        mSuggestionStripViewAccessor = mock(SuggestionStripViewAccessor.class);
        mDictionaryFacilitator = mock(DictionaryFacilitator.class);
        mKeyboard = mock(Keyboard.class);
        final ProximityInfo proximityInfo = mock(ProximityInfo.class);
        when(mKeyboard.getProximityInfo()).thenReturn(proximityInfo);
        mSettingsValues = mock(SettingsValues.class);
        mInputLogic = new InputLogic(
                mLatinIMELegacy, mSuggestionStripViewAccessor, mDictionaryFacilitator);
        mInputLogic.setAvroActiveForTest(true);
    }

    private void composeAscii(final String word) {
        mInputLogic.mWordComposer.reset();
        for (int i = 0; i < word.length(); i++) {
            final char c = word.charAt(i);
            mInputLogic.mWordComposer.applyProcessedEvent(
                    Event.createSoftwareKeypressEvent(c, c, Constants.NOT_A_COORDINATE,
                            Constants.NOT_A_COORDINATE, false));
        }
    }

    @Test
    public void testAvroAppendsUniqueDictionarySuggestionsAfterPhoneticCandidates() {
        composeAscii("ami");
        when(mDictionaryFacilitator.isActive()).thenReturn(true);
        when(mDictionaryFacilitator.getLocale()).thenReturn(new Locale("bn"));

        final SuggestionResults dictResults = new SuggestionResults(
                SuggestedWords.MAX_SUGGESTIONS, false, false);
        dictResults.add(new SuggestedWordInfo("আমি", "", 200,
                SuggestedWordInfo.KIND_CORRECTION, null, 0, 0));
        dictResults.add(new SuggestedWordInfo("আমার", "", 190,
                SuggestedWordInfo.KIND_COMPLETION, null, 0, 0));
        dictResults.add(new SuggestedWordInfo("আমরা", "", 180,
                SuggestedWordInfo.KIND_COMPLETION, null, 0, 0));
        dictResults.add(new SuggestedWordInfo("আমিও", "", 170,
                SuggestedWordInfo.KIND_COMPLETION, null, 0, 0));
        dictResults.add(new SuggestedWordInfo("আমাদের", "", 160,
                SuggestedWordInfo.KIND_COMPLETION, null, 0, 0));
        dictResults.add(new SuggestedWordInfo("আমাকে", "", 150,
                SuggestedWordInfo.KIND_COMPLETION, null, 0, 0));
        dictResults.add(new SuggestedWordInfo("আমাদেরকে", "", 140,
                SuggestedWordInfo.KIND_COMPLETION, null, 0, 0));
        dictResults.add(new SuggestedWordInfo("আমায়", "", 130,
                SuggestedWordInfo.KIND_COMPLETION, null, 0, 0));

        when(mDictionaryFacilitator.getSuggestionResults(
                any(ComposedData.class), any(NgramContext.class), any(Keyboard.class),
                any(SettingsValuesForSuggestion.class), anyInt(), anyInt()))
                .thenReturn(dictResults);

        final SuggestedWords[] captured = new SuggestedWords[1];
        mInputLogic.getSuggestedWords(mSettingsValues, mKeyboard, 0,
                SuggestedWords.INPUT_STYLE_TYPING, 1, new OnGetSuggestedWordsCallback() {
                    @Override
                    public void onGetSuggestedWords(final SuggestedWords suggestedWords) {
                        captured[0] = suggestedWords;
                    }
                });

        final ArgumentCaptor<ComposedData> dataCaptor = ArgumentCaptor.forClass(ComposedData.class);
        verify(mDictionaryFacilitator, atLeastOnce()).getSuggestionResults(
                dataCaptor.capture(), any(NgramContext.class), any(Keyboard.class),
                any(SettingsValuesForSuggestion.class), anyInt(), anyInt());
        assertEquals("আমি", dataCaptor.getAllValues().get(0).mTypedWord);

        final SuggestedWords words = captured[0];
        assertNotNull(words);
        assertTrue("Expected total suggestions capped at 8, got " + words.size(),
                words.size() <= 8);
        assertEquals("ami", words.getWord(0));
        assertEquals(SuggestedWordInfo.KIND_TYPED, words.getInfo(0).getKind());
        assertEquals("আমি", words.getWord(1));
        assertEquals(SuggestedWordInfo.KIND_CORRECTION, words.getInfo(1).getKind());

        final HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < words.size(); i++) {
            assertTrue("Duplicate suggestion at index " + i + ": " + words.getWord(i),
                    seen.add(words.getWord(i)));
        }
        assertTrue("Expected dictionary suggestion আমার to be appended", seen.contains("আমার"));
        assertTrue("Expected dictionary suggestion আমরা to be appended", seen.contains("আমরা"));
    }

    @Test
    public void testAvroDoesNotCrashWhenDictionaryNotLoaded() {
        composeAscii("ami");
        when(mDictionaryFacilitator.isActive()).thenReturn(false);

        final SuggestedWords[] captured = new SuggestedWords[1];
        mInputLogic.getSuggestedWords(mSettingsValues, null, 0,
                SuggestedWords.INPUT_STYLE_TYPING, 1, new OnGetSuggestedWordsCallback() {
                    @Override
                    public void onGetSuggestedWords(final SuggestedWords suggestedWords) {
                        captured[0] = suggestedWords;
                    }
                });

        final SuggestedWords words = captured[0];
        assertNotNull(words);
        assertEquals("ami", words.getWord(0));
        assertEquals("আমি", words.getWord(1));
    }
}
