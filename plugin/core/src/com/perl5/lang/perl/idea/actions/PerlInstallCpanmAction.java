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

package com.perl5.lang.perl.idea.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.perl5.PerlBundle;
import com.perl5.lang.perl.adapters.CpanminusAdapter;
import com.perl5.lang.perl.idea.project.PerlProjectManager;
import com.perl5.lang.perl.idea.sdk.versionManager.PerlVersionManagerData;
import org.jetbrains.annotations.NotNull;

public class PerlInstallCpanmAction extends PerlActionBase {
  public PerlInstallCpanmAction() {
    //noinspection DialogTitleCapitalization
    super(PerlBundle.message("perl.action.install.cpanm"));
  }

  @Override
  protected boolean alwaysHideDisabled() {
    return true;
  }

  @Override
  protected boolean isEnabled(@NotNull AnActionEvent event) {
    if (!super.isEnabled(event)) {
      return false;
    }
    Project project = event.getProject();
    if (project == null) {
      return false;
    }
    Sdk perlSdk = PerlProjectManager.getSdk(project);
    if (perlSdk == null) {
      return false;
    }
    return !CpanminusAdapter.isAvailable(perlSdk);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PerlVersionManagerData<?, ?> versionManagerData = PerlVersionManagerData.from(PerlProjectManager.getSdk(e));
    if (versionManagerData != null) {
      versionManagerData.installCpanminus(e.getProject());
    }
  }
}
