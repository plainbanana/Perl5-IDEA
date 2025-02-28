/*
 * Copyright 2015-2021 Alexandr Evstigneev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.perl5.lang.perl.intellilang;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.perl5.lang.perl.idea.configuration.settings.PerlSharedSettings;
import com.perl5.lang.perl.idea.intellilang.PerlInjectionMarkersService;
import com.perl5.lang.perl.lexer.PerlAnnotations;
import com.perl5.lang.perl.psi.PerlHeredocTerminatorElement;
import com.perl5.lang.perl.psi.impl.PerlHeredocElementImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


public class PerlHeredocLanguageInjector extends PerlLiteralLanguageInjector {
  private static final Logger LOG = Logger.getInstance(PerlHeredocLanguageInjector.class);
  private static final List<? extends Class<? extends PsiElement>> ELEMENTS_TO_INJECT =
    Collections.singletonList(PerlHeredocElementImpl.class);

  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    assert context instanceof PerlHeredocElementImpl;

    if (context.getTextLength() == 0 ||
        !PerlSharedSettings.getInstance(context.getProject()).AUTOMATIC_HEREDOC_INJECTIONS ||
        !((PerlHeredocElementImpl)context).isValidHost()) {
      return;
    }

    Language mappedLanguage = getInjectedLanguage((PerlHeredocElementImpl)context);
    if (mappedLanguage == null) {
      return;
    }
    registrar.startInjecting(mappedLanguage);
    addPlace((PerlHeredocElementImpl)context, registrar);
    registrar.doneInjecting();
  }

  private @Nullable Language getInjectedLanguage(@NotNull PerlHeredocElementImpl heredocElement) {
    PerlHeredocTerminatorElement terminator = heredocElement.getTerminatorElement();

    if (terminator == null) {
      return null;
    }

    var heredocOpener = terminator.getOpener();
    if (heredocOpener != null) {
      if (PerlAnnotations.isInjectionSuppressed(heredocOpener)) {
        return null;
      }
    }
    else {
      LOG.warn("Unable to find opener for terminator: " + terminator.getText() +
               " " + terminator.getTextRange() +
               " in " + PsiUtilCore.getVirtualFile(terminator));
    }

    String terminatorText = terminator.getText();
    Language mappedLanguage = PerlInjectionMarkersService.getInstance(terminator.getProject()).getLanguageByMarker(terminatorText);

    if (mappedLanguage != null) {
      return mappedLanguage;
    }

    PerlTemporaryInjectedLanguageDetector temporaryInjectedLanguageDetector = PerlTemporaryInjectedLanguageDetector.getInstance();
    return temporaryInjectedLanguageDetector == null
           ? null
           : temporaryInjectedLanguageDetector.getTemporaryInjectedLanguage(heredocElement);
  }

  private void addPlace(@NotNull PerlHeredocElementImpl heredocElement, @NotNull MultiHostRegistrar registrar) {
    int indentSize = heredocElement.getRealIndentSize();
    if (indentSize == 0) {
      registrar.addPlace(null, null, heredocElement, ElementManipulators.getValueTextRange(heredocElement));
      return;
    }

    CharSequence sourceText = heredocElement.getNode().getChars();

    int currentLineIndent = 0;
    int sourceOffset = 0;

    int sourceLength = sourceText.length();
    while (sourceOffset < sourceLength) {
      char currentChar = sourceText.charAt(sourceOffset);
      if (currentChar == '\n') {
        registrar.addPlace(null, null, heredocElement, TextRange.from(sourceOffset, 1));
        currentLineIndent = 0;
      }
      else if (Character.isWhitespace(currentChar) && currentLineIndent < indentSize) {
        currentLineIndent++;
      }
      else {
        // got non-space or indents ended, consume till the EOL or EOHost
        int sourceEnd = sourceOffset;
        while (sourceEnd < sourceLength) {
          currentChar = sourceText.charAt(sourceEnd);
          sourceEnd++;
          if (currentChar == '\n') {
            break;
          }
        }

        registrar.addPlace(null, null, heredocElement, TextRange.create(sourceOffset, sourceEnd));
        sourceOffset = sourceEnd;
        currentLineIndent = 0;
        continue;
      }
      sourceOffset++;
    }
  }

  @Override
  public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return ELEMENTS_TO_INJECT;
  }
}
