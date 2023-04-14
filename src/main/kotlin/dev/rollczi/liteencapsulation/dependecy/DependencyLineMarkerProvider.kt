package dev.rollczi.liteencapsulation.dependecy

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.elementType
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Function
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

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.Nodes.Artifact,
           Function { "Show dependencies" },
            ClickHandler(psiClass),
            GutterIconRenderer.Alignment.RIGHT,
            { "Dependency" }
        )
    }

    class ClickHandler(private val psiClass: PsiClass) : GutterIconNavigationHandler<PsiElement> {

        override fun navigate(mouseEvent: MouseEvent, element: PsiElement) {
            val usages = ReferencesSearch.search(psiClass, GlobalSearchScope.allScope(psiClass.project))
                .distinctBy { it.element.containingFile.virtualFile }
                .toMutableList()

            if (usages.isEmpty()) {
                usages.add(element.reference)
            }

            psiClass.project
            val popup = createPopup(usages, psiClass)

            JBPopupFactory.getInstance()
                .createListPopup(popup)
                .show(RelativePoint(mouseEvent))
        }

        private fun createPopup(usages: List<PsiReference>, currentPsiClass: PsiClass): ListPopupStep<PsiElement> {
            val listItems = usages
                .filterNotNull()
                .map { it.element }
                .sortedBy { fromPsiElements(currentPsiClass, it) }

            return object : BaseListPopupStep<PsiElement>("What classes look on this class?", listItems) {

                override fun getTextFor(element: PsiElement): String {
                    val javaFile = element.containingFile as? PsiJavaFile
                        ?: return "Unknown reference"

                    val className = javaFile.name
                        .replace(".java", "")

                    return "$className"
                }

                override fun getIconFor(value: PsiElement): Icon? {
                    val scope = fromPsiElements(currentPsiClass, value)

                    return when (scope) {
                        DependencyScope.UNKNOWN -> AllIcons.Nodes.Unknown
                        DependencyScope.PRIVATE -> AllIcons.Nodes.Private
                        DependencyScope.PROTECTED -> AllIcons.Nodes.Protected
                        DependencyScope.PACKAGE_PRIVATE -> AllIcons.Nodes.PackageLocal
                        DependencyScope.PUBLIC -> AllIcons.Nodes.Public
                    }
                }

                override fun onChosen(selectedValue: PsiElement, finalChoice: Boolean): PopupStep<*>? {
                    if (selectedValue is Navigatable) {
                        selectedValue.navigate(true)
                    }

                    return super.onChosen(selectedValue, finalChoice)
                }
            }
        }
    }
}