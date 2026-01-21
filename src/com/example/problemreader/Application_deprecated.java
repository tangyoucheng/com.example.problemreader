package com.example.problemreader;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.*;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;

/**
 * Eclipse Plugin Application
 * 功能：
 * - 调用 deprecated 方法 → 输出调用方法所在行 + 方法完整签名
 * - new 已弃用类 → 输出调用点 + 类完整 binary name + class/interface/enum/@interface
 * - 字段/参数/返回值 → 检测 deprecated 类型
 * - 继承/实现 → 检测父类 / 接口是否 deprecated
 * - 输出格式统一 → 内部类用 $，方法参数换行显示，方法/类/字段均显示修饰符
 */
public class Application_deprecated implements IApplication {

    private final Set<String> reported = new HashSet<>();

    @Override
    public Object start(IApplicationContext context) throws Exception {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        System.out.println("Workspace: " + root.getLocation());
        System.out.println("===================================");

        for (IProject project : root.getProjects()) {
            if (!project.isOpen()) continue;
            if (!project.hasNature(JavaCore.NATURE_ID)) continue;

            if (!"unicorn2.framework".equals(project.getName())) continue;

            System.out.println("\n[Project] " + project.getName());
            IJavaProject javaProject = JavaCore.create(project);

            for (IPackageFragmentRoot pfr : javaProject.getPackageFragmentRoots()) {
                if (pfr.getKind() != IPackageFragmentRoot.K_SOURCE) continue;

                for (IJavaElement pkg : pfr.getChildren()) {
                    if (!(pkg instanceof IPackageFragment)) continue;

                    for (ICompilationUnit unit : ((IPackageFragment) pkg).getCompilationUnits()) {
                        parse(unit);
                    }
                }
            }
        }
        return IApplication.EXIT_OK;
    }

    private void parse(ICompilationUnit unit) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setSource(unit);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);

        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        cu.accept(new DeprecatedVisitor(cu));
    }

    private class DeprecatedVisitor extends ASTVisitor {
        private final CompilationUnit cu;

        DeprecatedVisitor(CompilationUnit cu) {
            this.cu = cu;
        }

        // ------------------- 方法调用 -------------------
        @Override
        public boolean visit(MethodInvocation node) {
            IMethodBinding mb = node.resolveMethodBinding();
            if (mb != null && mb.isDeprecated()) {
                report(node, resolveCaller(node), formatMethod(mb));
            }
            return super.visit(node);
        }

        // ------------------- 构造函数 / new -------------------
        @Override
        public boolean visit(ClassInstanceCreation node) {
            IMethodBinding mb = node.resolveConstructorBinding();
            ITypeBinding tb = node.resolveTypeBinding();
            if (tb != null && tb.isDeprecated()) {
                report(node, resolveCaller(node), formatTypeWithKeyword(tb));
            }
            if (mb != null && mb.isDeprecated()) {
                report(node, resolveCaller(node), formatMethod(mb));
            }
            return super.visit(node);
        }

        // ------------------- 类 / 接口 / 枚举 / 注解 -------------------
        @Override
        public boolean visit(TypeDeclaration node) {
            ITypeBinding tb = node.resolveBinding();
            if (tb != null && tb.isDeprecated()) {
                report(node, resolveCaller(node), formatTypeWithKeyword(tb));
            }

            if (node.getSuperclassType() != null) {
                checkDeprecatedType(node, node.getSuperclassType(), "extends");
            }
            for (Object o : node.superInterfaceTypes()) {
                checkDeprecatedType(node, (Type) o, "implements");
            }
            return super.visit(node);
        }

        // ------------------- 字段 -------------------
        @Override
        public boolean visit(FieldDeclaration node) {
            int modifiers = node.getModifiers();
            checkDeprecatedType(node, node.getType(), "field", modifiers);
            return super.visit(node);
        }

        // ------------------- 方法 / 构造函数 -------------------
        @Override
        public boolean visit(MethodDeclaration node) {
            int modifiers = node.getModifiers();
            if (node.getReturnType2() != null) {
                checkDeprecatedType(node, node.getReturnType2(), "return", modifiers);
            }
            if (node.resolveBinding() != null && node.resolveBinding().isDeprecated()) {
                report(node, resolveCaller(node), formatMethod(node.resolveBinding()));
            }
            return super.visit(node);
        }

        // ------------------- 方法参数 -------------------
        @Override
        public boolean visit(SingleVariableDeclaration node) {
            int modifiers = node.getModifiers();
            checkDeprecatedType(node, node.getType(), "param", modifiers);
            return super.visit(node);
        }

        // =================== 核心辅助方法 ===================
        private void checkDeprecatedType(ASTNode location, Type type, String role) {
            checkDeprecatedType(location, type, role, 0);
        }

        private void checkDeprecatedType(ASTNode location, Type type, String role, int modifiers) {
            ITypeBinding tb = type.resolveBinding();
            if (tb == null || !tb.isDeprecated()) return;

            String desc = (formatModifiers(tb.getModifiers()).isEmpty() ? "" : formatModifiers(tb.getModifiers()) + " ")
                    + formatTypeWithKeyword(tb);
            report(location, resolveCaller(location), desc);
        }

        private void report(ASTNode node, String caller, String callee) {
            if (caller == null || callee == null) return;
            int line = cu.getLineNumber(node.getStartPosition());
            String key = caller + " -> " + callee;
            if (reported.add(key)) {
                System.out.println(caller + "  (line " + line + ")");
                System.out.println("  -> " + callee);
            }
        }

        private String resolveCaller(ASTNode node) {
            ASTNode cur = node;
            while (cur != null) {
                if (cur instanceof MethodDeclaration md) {
                    IMethodBinding mb = md.resolveBinding();
                    if (mb == null) return null;
                    return formatMethod(mb);
                }
                if (cur instanceof TypeDeclaration td) {
                    ITypeBinding tb = td.resolveBinding();
                    if (tb == null) return null;
                    return formatModifiers(tb.getModifiers()) + " " + formatTypeWithKeyword(tb);
                }
                cur = cur.getParent();
            }
            return null;
        }

        // =================== 方法 / 构造函数格式化 ===================
        private String formatMethod(IMethodBinding mb) {
            String owner = formatType(mb.getDeclaringClass());
            String mods = formatModifiers(mb.getModifiers());

            String params;
            if (mb.getParameterTypes().length == 0) {
                params = "";
            } else {
                params = "\n    " + Arrays.stream(mb.getParameterTypes())
                        .map(this::formatType)
                        .collect(Collectors.joining(",\n    ")) + "\n";
            }

            String returnType = formatType(mb.getReturnType());

            return (mods.isEmpty() ? "" : mods + " ") + returnType + " " + owner + "." + mb.getName() + "(" + params + ")";
        }

        // =================== 类型格式化 ===================
        private String formatType(ITypeBinding tb) {
            if (tb == null) return "unknown";
            if (tb.isArray()) return formatType(tb.getElementType()) + "[]";
            if (tb.isPrimitive()) return tb.getName();
            String binary = tb.getBinaryName();
            return binary != null ? binary : tb.getQualifiedName();
        }

        // =================== 类型 + 类关键字 ===================
        private String formatTypeWithKeyword(ITypeBinding tb) {
            if (tb == null) return "unknown";

            String kind;
            if (tb.isAnnotation()) kind = "@interface";
            else if (tb.isEnum()) kind = "enum";
            else if (tb.isInterface()) kind = "interface";
            else kind = "class";

            return kind + " " + formatType(tb);
        }

        private String formatModifiers(int mods) {
            return Modifier.toString(mods);
        }
    }

    @Override
    public void stop() {}
}
