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

package com.perl5.lang.perl.idea.hierarchy.namespace;

import com.intellij.ide.hierarchy.*;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.psi.PsiElement;
import com.perl5.lang.perl.idea.hierarchy.namespace.treestructures.PerlSubTypesHierarchyTreeStructure;
import com.perl5.lang.perl.idea.hierarchy.namespace.treestructures.PerlSuperTypesHierarchyTreeStructure;
import com.perl5.lang.perl.psi.PerlNamespaceDefinitionElement;
import com.perl5.lang.perl.psi.properties.PerlIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;


public class PerlHierarchyBrowser extends TypeHierarchyBrowserBase {
  public PerlHierarchyBrowser(PsiElement element) {
    super(element.getProject(), element);
  }

  @Override
  protected boolean isInterface(@NotNull PsiElement psiElement) {
    return false;
  }

  @Override
  protected boolean canBeDeleted(PsiElement psiElement) {
    return false;
  }

  @Override
  protected String getQualifiedName(PsiElement psiElement) {
    if (psiElement instanceof PerlIdentifierOwner) {
      return ((PerlIdentifierOwner)psiElement).getPresentableName();
    }

    return null;
  }

  @Override
  protected @Nullable String getContentDisplayName(@NotNull String typeName, @NotNull PsiElement element) {
    return super.getContentDisplayName(typeName, element);
  }

  @Override
  protected @Nullable PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    if (!(descriptor instanceof PerlHierarchyNodeDescriptor)) {
      return null;
    }
    return ((PerlHierarchyNodeDescriptor)descriptor).getPerlElement();
  }

  @Override
  protected void createTrees(@NotNull Map<? super String, ? super JTree> trees) {
    trees.put(SUPERTYPES_HIERARCHY_TYPE, createTree(true));
    trees.put(SUBTYPES_HIERARCHY_TYPE, createTree(true));
    trees.put(TYPE_HIERARCHY_TYPE, createTree(true));
  }

  @Override
  protected void prependActions(final @NotNull DefaultActionGroup actionGroup) {
    actionGroup.add(new ViewSupertypesHierarchyAction());
    actionGroup.add(new ViewSubtypesHierarchyAction());
    actionGroup.add(new AlphaSortAction());
  }


  @Override
  protected @Nullable JPanel createLegendPanel() {
    return null;
  }

  @Override
  protected boolean isApplicableElement(@NotNull PsiElement element) {
    return element instanceof PerlNamespaceDefinitionElement;
  }

  @Override
  protected @Nullable HierarchyTreeStructure createHierarchyTreeStructure(@NotNull String typeName, @NotNull PsiElement psiElement) {
    if (SUPERTYPES_HIERARCHY_TYPE.equals(typeName)) {
      return getSuperTypesHierarchyStructure(psiElement);
    }
    else if (SUBTYPES_HIERARCHY_TYPE.equals(typeName)) {
      return getSubTypesHierarchyStructure(psiElement);
    }
    else if (TYPE_HIERARCHY_TYPE.equals(typeName)) {
      return getTypesHierarchyStructure(psiElement);
    }
    return null;
  }

  @Override
  protected @Nullable Comparator<NodeDescriptor<?>> getComparator() {
    return null;
  }

  protected @Nullable HierarchyTreeStructure getSuperTypesHierarchyStructure(PsiElement psiElement) {
    return new PerlSuperTypesHierarchyTreeStructure(psiElement);
  }

  protected @Nullable HierarchyTreeStructure getSubTypesHierarchyStructure(PsiElement psiElement) {
    return new PerlSubTypesHierarchyTreeStructure(psiElement);
  }

  protected @Nullable HierarchyTreeStructure getTypesHierarchyStructure(PsiElement psiElement) {
    return null;
  }
}
