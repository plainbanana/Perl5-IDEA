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

package com.perl5.lang.perl.idea.project;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.PerlModuleExtension;
import com.intellij.openapi.projectRoots.impl.PerlSdkTable;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtil.ImmutableMapBuilder;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.messages.MessageBusConnection;
import com.perl5.PerlBundle;
import com.perl5.lang.perl.idea.configuration.settings.PerlLocalSettings;
import com.perl5.lang.perl.idea.configuration.settings.sdk.Perl5SettingsConfigurable;
import com.perl5.lang.perl.idea.configuration.settings.sdk.PerlSdkLibrary;
import com.perl5.lang.perl.idea.modules.PerlLibrarySourceRootType;
import com.perl5.lang.perl.idea.modules.PerlSourceRootType;
import com.perl5.lang.perl.idea.sdk.PerlSdkType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.intellij.ProjectTopics.PROJECT_ROOTS;

public class PerlProjectManager implements Disposable {
  private final @NotNull Project myProject;
  private final PerlLocalSettings myPerlSettings;
  private final Map<PerlSourceRootType, List<VirtualFile>> myModulesRootsProvider;
  private volatile AtomicNullableLazyValue<Sdk> mySdkProvider;
  private volatile AtomicNotNullLazyValue<Map<VirtualFile, PerlSourceRootType>> myAllModulesMapProvider;
  private volatile AtomicNotNullLazyValue<List<VirtualFile>> myExternalLibraryRootsProvider;
  private volatile AtomicNotNullLazyValue<List<VirtualFile>> mySdkLibraryRootsProvider;
  private volatile AtomicNotNullLazyValue<List<VirtualFile>> myLibraryRootsProvider;
  private volatile AtomicNotNullLazyValue<List<SyntheticLibrary>> myLibrariesProvider;

  public PerlProjectManager(@NotNull Project project) {
    myProject = project;
    myPerlSettings = PerlLocalSettings.getInstance(project);
    myModulesRootsProvider = FactoryMap.create(type -> type.getRoots(myProject));
    resetProjectCaches();
    MessageBusConnection connection = myProject.getMessageBus().connect(this);
    connection.subscribe(PerlSdkTable.PERL_TABLE_TOPIC, new ProjectJdkTable.Listener() {
      @Override
      public void jdkAdded(@NotNull Sdk jdk) {

      }

      @Override
      public void jdkRemoved(@NotNull Sdk sdk) {
        if (StringUtil.equals(sdk.getName(), myPerlSettings.getPerlInterpreter())) {
          setProjectSdk(null);
        }
      }

      @Override
      public void jdkNameChanged(@NotNull Sdk jdk, @NotNull String previousName) {
        if (StringUtil.equals(myPerlSettings.getPerlInterpreter(), previousName)) {
          setProjectSdk(jdk);
        }
      }
    });
    connection.subscribe(PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        resetProjectCaches();
      }
    });
    VirtualFileListener listener = new VirtualFileListener() {
      @Override
      public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        resetProjectCaches();
      }

      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        resetProjectCaches();
      }

      @Override
      public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        resetProjectCaches();
      }
    };
    LocalFileSystem.getInstance().addVirtualFileListener(listener);
    Disposer.register(this, () -> LocalFileSystem.getInstance().removeVirtualFileListener(listener));
  }

  @Override
  public void dispose() {

  }

  private void resetProjectCaches() {
    myModulesRootsProvider.clear();
    myAllModulesMapProvider = AtomicNotNullLazyValue.createValue(() -> {
      ImmutableMapBuilder<VirtualFile, PerlSourceRootType> builder = ContainerUtil.immutableMapBuilder();
      for (Module module : ModuleManager.getInstance(myProject).getModules()) {
        PerlModuleExtension.getInstance(module).getRoots().forEach(builder::put);
      }

      return builder.build();
    });
    mySdkProvider = AtomicNullableLazyValue.createValue(() -> PerlSdkTable.getInstance().findJdk(myPerlSettings.getPerlInterpreter()));
    myExternalLibraryRootsProvider = AtomicNotNullLazyValue.createValue(() -> {
      List<VirtualFile> result = new ArrayList<>();

      for (String externalPath : myPerlSettings.getExternalLibrariesPaths()) {
        VirtualFile virtualFile = VfsUtil.findFileByIoFile(new File(externalPath), false);
        if (virtualFile != null && virtualFile.isValid() && virtualFile.isDirectory()) {
          result.add(virtualFile);
        }
      }
      return result;
    });
    mySdkLibraryRootsProvider = AtomicNotNullLazyValue.createValue(() -> {
      List<VirtualFile> result = new ArrayList<>(getExternalLibraryRoots());
      Sdk projectSdk = getProjectSdk();
      if (projectSdk != null) {
        result.addAll(Arrays.asList(projectSdk.getRootProvider().getFiles(OrderRootType.CLASSES)));
      }
      return result;
    });
    myLibraryRootsProvider = AtomicNotNullLazyValue.createValue(() -> {
      ArrayList<VirtualFile> result = new ArrayList<>(getModulesLibraryRoots());
      result.addAll(getProjectSdkLibraryRoots());
      return result;
    });
    myLibrariesProvider = AtomicNotNullLazyValue.createValue(() -> {
      List<VirtualFile> sdkLibs = getProjectSdkLibraryRoots();
      if (sdkLibs.isEmpty()) {
        return Collections.emptyList();
      }

      SyntheticLibrary sdkLibrary;
      Sdk sdk = getProjectSdk();
      if (sdk != null) {
        sdkLibrary = new PerlSdkLibrary(sdk, sdkLibs);
      }
      else {
        sdkLibrary = SyntheticLibrary.newImmutableLibrary(sdkLibs);
      }
      return Collections.singletonList(sdkLibrary);
    });
  }

  public Map<VirtualFile, PerlSourceRootType> getAllModulesRoots() {
    return myAllModulesMapProvider.getValue();
  }

  public List<VirtualFile> getExternalLibraryRoots() {
    return myExternalLibraryRootsProvider.getValue();
  }

  public List<VirtualFile> getModulesLibraryRoots() {
    return getModulesRootsOfType(PerlLibrarySourceRootType.INSTANCE);
  }

  public List<VirtualFile> getModulesRootsOfType(@NotNull PerlSourceRootType type) {
    return myModulesRootsProvider.get(type);
  }

  public List<VirtualFile> getAllLibraryRoots() {
    return myLibraryRootsProvider.getValue();
  }

  public List<VirtualFile> getProjectSdkLibraryRoots() {
    return mySdkLibraryRootsProvider.getValue();
  }

  public List<SyntheticLibrary> getProjectLibraries() { return myLibrariesProvider.getValue();}

  public @Nullable Sdk getProjectSdk() {
    return mySdkProvider.getValue();
  }

  public void setProjectSdk(@Nullable Sdk sdk) {
    WriteAction.run(
      () -> ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(
        () -> myPerlSettings.setPerlInterpreter(sdk == null ? null : sdk.getName()), false, true)
    );
  }

  public void addExternalLibrary(@NotNull VirtualFile root) {
    addExternalLibraries(Collections.singletonList(root));
  }

  public boolean isPerlEnabled() {
    return !myProject.isDefault() && getProjectSdk() != null;
  }

  public void setExternalLibraries(@NotNull List<VirtualFile> roots) {
    WriteAction.run(() -> {
      myPerlSettings.setExternalLibrariesPaths(Collections.emptyList());
      addExternalLibraries(roots);
    });
  }

  public void addExternalLibraries(@NotNull List<VirtualFile> roots) {
    WriteAction.run(
      () -> {
        List<String> paths = new ArrayList<>(myPerlSettings.getExternalLibrariesPaths());
        boolean doChange = false;
        for (VirtualFile root : roots) {
          if (!root.isValid() || !root.isDirectory()) {
            return;
          }

          String canonicalPath = root.getCanonicalPath();
          if (!paths.contains(canonicalPath)) {
            doChange = true;
            paths.add(canonicalPath);
          }
        }
        if (!doChange && !paths.isEmpty()) {
          return;
        }

        ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(() -> myPerlSettings.setExternalLibrariesPaths(paths), false, true);
      }
    );
  }

  public static PerlProjectManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PerlProjectManager.class);
  }


  @Contract("null->null")
  public static @Nullable Sdk getSdk(@Nullable Module module) {
    return module == null ? null : getInstance(module.getProject()).getProjectSdk();
  }

  /**
   * @return sdk for project. If not configured - suggests to configure
   */
  public static Sdk getSdkWithNotification(@NotNull Project project) {
    Sdk sdk = getSdk(project);
    if (sdk != null) {
      return sdk;
    }
    showUnconfiguredInterpreterNotification(project);
    return null;
  }

  public static void showUnconfiguredInterpreterNotification(@NotNull Project project) {
    Notification notification = new Notification(
      PerlBundle.message("perl.select.sdk.notification"),
      PerlBundle.message("perl.select.sdk.notification.title"),
      PerlBundle.message("perl.select.sdk.notification.message"),
      NotificationType.ERROR
    );
    notification.addAction(new DumbAwareAction(PerlBundle.message("perl.configure.interpreter.action")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        Perl5SettingsConfigurable.open(project);
        notification.expire();
      }
    });
    Notifications.Bus.notify(notification, project);
  }

  @Contract("null->null")
  public static @Nullable Sdk getSdk(@Nullable Project project) {
    return project == null ? null : getInstance(project).getProjectSdk();
  }

  public static @Nullable String getInterpreterPath(@Nullable Module module) {
    return getInterpreterPath(getSdk(module));
  }

  /**
   * Migration method for 2018.3. Updates sdk path to interpreter path, not bin dir
   *
   * @param sdk in question
   * @return path to interpreter
   */
  @Contract("null->null")
  public static @Nullable String getInterpreterPath(@Nullable Sdk sdk) {
    if (sdk == null) {
      return null;
    }
    String homePath = sdk.getHomePath();
    if (homePath != null && !StringUtil.contains(new File(homePath).getName(), "perl")) {
      homePath = FileUtil.join(homePath, PerlSdkType.INSTANCE.getPerlExecutableName());
      SdkModificator modificator = sdk.getSdkModificator();
      modificator.setHomePath(homePath);
      modificator.commitChanges();
    }
    return homePath;
  }

  public static @Nullable String getInterpreterPath(@Nullable Project project) {
    return getInterpreterPath(getSdk(project));
  }

  public static @Nullable Sdk getSdk(@NotNull Project project, @Nullable VirtualFile virtualFile) {
    if (virtualFile == null) {
      return getSdk(project);
    }
    Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
    return module == null ? getSdk(project) : getSdk(module);
  }

  public static boolean isPerlEnabled(@Nullable Project project) {
    return project != null && !project.isDisposed() && getInstance(project).isPerlEnabled();
  }

  public static boolean isPerlEnabled(@NotNull DataContext dataContext) {
    return isPerlEnabled(CommonDataKeys.PROJECT.getData(dataContext));
  }

  public static boolean isPerlEnabled(@Nullable Module module) {
    return module != null && isPerlEnabled(module.getProject());
  }

  @Contract("null->null")
  public static @Nullable Sdk getSdk(@Nullable AnActionEvent event) {
    return event == null ? null : getSdk(event.getProject());
  }
}
