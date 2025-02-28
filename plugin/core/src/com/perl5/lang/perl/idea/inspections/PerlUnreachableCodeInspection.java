/*
 * Copyright 2015-2020 Alexandr Evstigneev
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

package com.perl5.lang.perl.idea.inspections;

import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.perl5.PerlBundle;
import com.perl5.lang.perl.idea.codeInsight.controlFlow.PerlControlFlowBuilder;
import com.perl5.lang.perl.psi.PerlMethodModifier;
import com.perl5.lang.perl.psi.PerlSubDefinitionElement;
import com.perl5.lang.perl.psi.PerlVisitor;
import com.perl5.lang.perl.psi.PsiPerlSubExpr;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.perl5.lang.perl.lexer.PerlElementTypesGenerated.*;

public class PerlUnreachableCodeInspection extends PerlInspection {
  private final TokenSet TRANSPARENT_ELEMENTS = TokenSet.create(
    COMMA_SEQUENCE_EXPR, DO_BLOCK_EXPR, PARENTHESISED_EXPR, DEREF_EXPR, SUB_EXPR, SUB_DEFINITION, METHOD_DEFINITION, FUNC_DEFINITION,
    LP_OR_XOR_EXPR, EVAL_EXPR
  );

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    Set<PsiElement> notifiedElements = new HashSet<>();
    return new PerlVisitor() {
      @Override
      public void visitPerlSubDefinitionElement(@NotNull PerlSubDefinitionElement o) {
        processElement(o);
      }

      @Override
      public void visitPerlMethodModifier(@NotNull PerlMethodModifier o) {
        processElement(o);
      }

      private void processElement(@NotNull PsiElement o) {
        PerlControlFlowBuilder.iteratePrev(o, instruction -> {
          if (instruction.allPred().isEmpty() && instruction.num() != 0) {
            processInstruction(instruction);
          }
          return ControlFlowUtil.Operation.NEXT;
        });
      }

      @Override
      public void visitSubExpr(@NotNull PsiPerlSubExpr o) {
        processElement(o);
      }

      @Override
      public void visitFile(@NotNull PsiFile file) {
        processElement(file);
      }

      private void processInstruction(@NotNull Instruction instruction) {
        PsiElement element = instruction.getElement();
        if (element == null || !notifiedElements.add(element)) {
          return;
        }

        if (TRANSPARENT_ELEMENTS.contains(PsiUtilCore.getElementType(element))) {
          instruction.allSucc().forEach(it -> {
            // this is just a weak check for next unreachable instruction
            if (it.allPred().size() < 2) {
              processInstruction(it);
            }
          });
        }
        else {
          registerProblem(holder, element, PerlBundle.message("perl.inspection.unreachable.code"));
        }
      }
    };
  }
}
