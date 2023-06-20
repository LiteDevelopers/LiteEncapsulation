package dev.rollczi.liteencapsulation.dependecy

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile


fun scopeOf(from: PsiElement, to: PsiElement): DependencyScope {
    val currentFile = from.containingFile as? PsiJavaFile
        ?: return DependencyScope.UNKNOWN
    val otherFile = to.containingFile as? PsiJavaFile
        ?: return DependencyScope.UNKNOWN

    if (currentFile == otherFile) {
        return DependencyScope.PRIVATE
    }

    val currentPackage = currentFile.packageName
    val otherPackage = otherFile.packageName

    if (otherPackage == currentPackage) {
        return DependencyScope.PACKAGE_PRIVATE
    }

    return DependencyScope.PUBLIC
}

enum class DependencyScope {

    PRIVATE,
    PACKAGE_PRIVATE,
    PROTECTED,
    PUBLIC,
    UNKNOWN,

}