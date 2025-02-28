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

package com.perl5.lang.perl.idea.completion.providers.processors;

import org.jetbrains.annotations.NotNull;

public class PerlSimpleDelegatingCompletionProcessor extends PerlDelegatingCompletionProcessor<PerlCompletionProcessor> {

  public PerlSimpleDelegatingCompletionProcessor(@NotNull PerlCompletionProcessor processor) {
    super(processor);
  }

  @Override
  public @NotNull PerlSimpleDelegatingCompletionProcessor withPrefixMatcher(@NotNull String prefix) {
    try {
      PerlSimpleDelegatingCompletionProcessor clone = (PerlSimpleDelegatingCompletionProcessor)clone();
      clone.setDelegate(getDelegate().withPrefixMatcher(prefix));
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
}
