package dev.rollczi.liteencapsulation.dependecy

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import com.intellij.ui.awt.RelativePoint
import dev.rollczi.liteencapsulation.dependecy.DependencyLineMarkerProvider.ClickHandler.ClassesLookupPopup.*
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.toUElementOfType
import java.awt.event.MouseEvent
import javax.swing.Icon

class DependencyLineMarkerProvider : LineMarkerProviderDescriptor() {

    override fun getName(): String = "Dependency line marker"

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (!element.isValid) {
            return null
        }

        val identifier = element.toUElementOfType<UIdentifier>()
            ?: return null

        val psiClass = (identifier.uastParent as? UClass)?.javaPsi
            ?: return null

        if (psiClass.elementType != JavaElementType.CLASS) {
            return null
        }

        if (!psiClass.isValid) {
            return null
        }

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.Nodes.Artifact,
            { "Show dependencies" },
            ClickHandler(psiClass),
            GutterIconRenderer.Alignment.RIGHT
        ) { "Dependency" }
    }

    class ClickHandler(private val psiClass: PsiClass) : GutterIconNavigationHandler<PsiElement> {

        override fun navigate(mouseEvent: MouseEvent, element: PsiElement) {
            val usages = ReferencesSearch.search(psiClass)
                .asSequence()
                .distinctBy { it.element.containingFile.virtualFile }
                .filterNotNull()
                .filter { isNotImport(it.element.parent.parent.elementType) }
                .map { it.element }
                .mapNotNull { psiElement -> toPackage(psiElement)?.let { packageName -> Item(scopeOf(psiClass, psiElement), psiElement, packageName) } }
                .sortedBy { it.dependencyScope }
                .sortedBy { it.packageName }
                .filter { it.dependencyScope != DependencyScope.UNKNOWN && it.dependencyScope != DependencyScope.PRIVATE }
                .toList()

            JBPopupFactory.getInstance()
                .createListPopup(ClassesLookupPopup(usages))
                .show(RelativePoint(mouseEvent))
        }

        private class ClassesLookupPopup(usages: List<Item>): BaseListPopupStep<Item>("What classes look on this class?", usages) {

            override fun getTextFor(element: Item): String {
                val javaFile = element.psiElement.containingFile as? PsiJavaFile
                    ?: return "Unknown reference"

                return javaFile.name
                    .replace(".java", "")
            }

            override fun getIconFor(value: Item): Icon {
                return when (value.dependencyScope) {
                    DependencyScope.UNKNOWN -> AllIcons.Nodes.Unknown
                    DependencyScope.PRIVATE -> AllIcons.Nodes.Private
                    DependencyScope.PROTECTED -> AllIcons.Nodes.Protected
                    DependencyScope.PACKAGE_PRIVATE -> AllIcons.Nodes.PackageLocal
                    DependencyScope.PUBLIC -> AllIcons.Nodes.Public
                }
            }

            private val separatedByPackage = mutableListOf<String>()

            override fun getSeparatorAbove(value: Item): ListSeparator? {
                if (separatedByPackage.contains(value.packageName)) {
                    return null
                }

                separatedByPackage.add(value.packageName)
                return ListSeparator(value.packageName)
            }

            override fun onChosen(selectedValue: Item, finalChoice: Boolean): PopupStep<*>? {
                if (selectedValue.psiElement is Navigatable) {
                    selectedValue.psiElement.navigate(true)
                }

                return PopupStep.FINAL_CHOICE
            }

            data class Item(val dependencyScope: DependencyScope, val psiElement: PsiElement, val packageName: String)
        }

    }
}

fun toPackage(from: PsiElement): String? {
    if (from.containingFile is PsiJavaFile) {
        return (from.containingFile as PsiJavaFile).packageName
    }

    return null
}

fun isNotImport(elementType: IElementType?): Boolean {
    return elementType != JavaElementType.IMPORT_LIST
        && elementType != JavaElementType.IMPORT_STATEMENT
        && elementType != JavaElementType.IMPORT_STATIC_STATEMENT
        && elementType != JavaElementType.IMPORT_STATIC_REFERENCE
}