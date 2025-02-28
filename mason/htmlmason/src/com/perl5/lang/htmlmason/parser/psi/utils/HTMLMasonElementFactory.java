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

package com.perl5.lang.htmlmason.parser.psi.utils;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.perl5.lang.htmlmason.filetypes.HTMLMasonFileType;
import com.perl5.lang.htmlmason.parser.psi.impl.HTMLMasonFileImpl;
import com.perl5.lang.perl.psi.PerlString;
import org.jetbrains.annotations.Nullable;


public class HTMLMasonElementFactory {
  public static @Nullable PerlString getBareCallString(Project project, String content) {
    String fileContent = "<& " + content + "&>";
    HTMLMasonFileImpl file = createFile(project, fileContent);
    return PsiTreeUtil.findChildOfType(file, PerlString.class);
  }

  public static HTMLMasonFileImpl createFile(Project project, String text) {
    return createFile(project, text, HTMLMasonFileType.INSTANCE);
  }

  public static HTMLMasonFileImpl createFile(Project project, String text, FileType fileType) {
    return (HTMLMasonFileImpl)PsiFileFactory.getInstance(project).
      createFileFromText("file.dummy", fileType, text);
  }
}
