package com.example.problemreader;

import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import cn.com.platform.framework.file.ExcelMakeFile;

/**
 * Eclipse Plugin Application
 * 功能：
 * - 先输出编译错误 → 全限定类名 + 行号 + 错误信息
 * - 再输出 deprecated 使用 → 调用方法 / new 已弃用类 / 字段/参数/返回值 / 继承/实现
 * - 内部类用 $，方法参数换行显示
 * - 方法/类/字段均显示修饰符
 */
public class Application implements IApplication {

    // 用于去重，避免重复输出相同信息
    private final Set<String> reported = new HashSet<>();

    @Override
    public Object start(IApplicationContext context) throws Exception {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        System.out.println("Workspace: " + root.getLocation());
        System.out.println("===================================");
        
        String projectPath = Paths.get("C:\\workspace_rcp\\com.example.problemreader").toAbsolutePath().toString();
        ExcelMakeFile excelMakeFile = new ExcelMakeFile(new File(projectPath+"/resources/Spring移行修正一覧.xlsx"));
        

        // 遍历工作区所有项目
        for (IProject project : root.getProjects()) {
            if (!project.isOpen()) continue;                   // 忽略未打开的项目
            if (!project.hasNature(JavaCore.NATURE_ID)) continue; // 忽略非 Java 项目
            if (!"unicorn3.framework".equals(project.getName())) continue;

            System.out.println("\n[Project] " + project.getName());
            IJavaProject javaProject = JavaCore.create(project);

            // 遍历项目中的 source 根路径
            for (IPackageFragmentRoot pfr : javaProject.getPackageFragmentRoots()) {
                if (pfr.getKind() != IPackageFragmentRoot.K_SOURCE) continue;

                // 遍历包
                for (IJavaElement pkg : pfr.getChildren()) {
                    if (!(pkg instanceof IPackageFragment)) continue;

                    // 遍历 Java 文件
                    for (ICompilationUnit unit : ((IPackageFragment) pkg).getCompilationUnits()) {
                        // 1️⃣ 先输出编译错误信息
                        reportProblems(unit, javaProject);

                        // 2️⃣ 再解析 AST 输出已弃用 API
//                        parse(unit);
                    }
                }
            }

            // 遍历项目中的 source 根路径
            for (IPackageFragmentRoot pfr : javaProject.getPackageFragmentRoots()) {
                if (pfr.getKind() != IPackageFragmentRoot.K_SOURCE) continue;

                // 遍历包
                for (IJavaElement pkg : pfr.getChildren()) {
                    if (!(pkg instanceof IPackageFragment)) continue;

                    // 遍历 Java 文件
                    for (ICompilationUnit unit : ((IPackageFragment) pkg).getCompilationUnits()) {
                        // 1️⃣ 先输出编译错误信息
//                        reportProblems(unit, javaProject);

                        // 2️⃣ 再解析 AST 输出已弃用 API
                        parse(unit);
                    }
                }
            }
            
            
        }
        return IApplication.EXIT_OK;
    }

    /**
     * 使用 ASTParser 解析单个 Java 文件
     */
    private void parse(ICompilationUnit unit) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setSource(unit);
        parser.setResolveBindings(true);     // 开启绑定解析
        parser.setBindingsRecovery(true);    // 尽量恢复绑定失败

        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        cu.accept(new DeprecatedVisitor(cu, unit)); // 使用自定义 Visitor
    }

    /**
     * 输出编译错误，通过 IMarker 获取
     */
    private void reportProblems(ICompilationUnit unit, IJavaProject javaProject) throws CoreException {
        IMarker[] markers = unit.getResource().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
        if (markers == null) return;

        for (IMarker marker : markers) {
            Integer severity = (Integer) marker.getAttribute(IMarker.SEVERITY);
            if (severity == null || severity != IMarker.SEVERITY_ERROR) continue;

            int line = marker.getAttribute(IMarker.LINE_NUMBER, -1);
            String message = marker.getAttribute(IMarker.MESSAGE, "unknown error");

            // 避免重复输出
            String key = unit.getElementName() + ":" + line + " -> " + message;
            if (!reported.add(key)) continue;

            try {
                // 获取顶层类全限定名
                IType[] types = unit.getAllTypes();
                String fullName = "unknown";
                if (types != null && types.length > 0) {
                    IType type = types[0];
                    fullName = type.getFullyQualifiedName(); // 包含 package
                }
                System.out.println(fullName + "  (line " + line + ")");
                System.out.println("  -> ERROR: " + message);
            } catch (JavaModelException e) {
                System.out.println(unit.getElementName() + "  (line " + line + ")");
                System.out.println("  -> ERROR: " + message);
            }
        }
    }

    /**
     * AST Visitor，用于检测 deprecated 使用
     */
    private class DeprecatedVisitor extends ASTVisitor {
        private final CompilationUnit cu;
        private final ICompilationUnit unit;

        DeprecatedVisitor(CompilationUnit cu, ICompilationUnit unit) {
            this.cu = cu;
            this.unit = unit;
        }

        // ---------- 调用 deprecated 方法 ----------
        @Override
        public boolean visit(MethodInvocation node) {
            IMethodBinding mb = node.resolveMethodBinding();
            if (mb != null && mb.isDeprecated()) {
                report(node, resolveCaller(node), formatMethod(mb));
            }
            return super.visit(node);
        }

        // ---------- new 已弃用类 / 调用 deprecated 构造 ----------
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

        // ---------- 类声明，继承/实现 ----------
        @Override
        public boolean visit(TypeDeclaration node) {
            ITypeBinding tb = node.resolveBinding();
            if (tb != null && tb.isDeprecated()) {
                report(node, resolveCaller(node), formatTypeWithKeyword(tb));
            }

            // 检查父类 deprecated
            if (node.getSuperclassType() != null) {
                checkDeprecatedType(node, node.getSuperclassType(), "extends");
            }
            // 检查接口 deprecated
            for (Object o : node.superInterfaceTypes()) {
                checkDeprecatedType(node, (Type) o, "implements");
            }
            return super.visit(node);
        }

        // ---------- 字段类型 ----------
        @Override
        public boolean visit(FieldDeclaration node) {
            int modifiers = node.getModifiers();
            checkDeprecatedType(node, node.getType(), "field", modifiers);
            return super.visit(node);
        }

        // ---------- 方法返回值 ----------
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

        // ---------- 方法参数 ----------
        @Override
        public boolean visit(SingleVariableDeclaration node) {
            int modifiers = node.getModifiers();
            checkDeprecatedType(node, node.getType(), "param", modifiers);
            return super.visit(node);
        }

        // ---------- 核心检查 deprecated 类型 ----------
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

        // ---------- 输出信息 ----------
        private void report(ASTNode node, String caller, String callee) {
            if (caller == null || callee == null) return;
            int line = cu.getLineNumber(node.getStartPosition());
            String key = caller + " -> " + callee;
            if (reported.add(key)) {
                System.out.println(caller + "  (line " + line + ")");
                System.out.println("  -> " + callee);
            }
        }

        // ---------- 获取调用者 ----------
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

        // ---------- 格式化方法签名 ----------
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

        // ---------- 格式化类型 ----------
        private String formatType(ITypeBinding tb) {
            if (tb == null) return "unknown";
            if (tb.isArray()) return formatType(tb.getElementType()) + "[]";
            if (tb.isPrimitive()) return tb.getName();
            String binary = tb.getBinaryName(); // 内部类用 $
            return binary != null ? binary : tb.getQualifiedName();
        }

        // ---------- 类型 + 关键字 ----------
        private String formatTypeWithKeyword(ITypeBinding tb) {
            if (tb == null) return "unknown";

            String kind;
            if (tb.isAnnotation()) kind = "@interface";
            else if (tb.isEnum()) kind = "enum";
            else if (tb.isInterface()) kind = "interface";
            else kind = "class";

            return kind + " " + formatType(tb);
        }

        // ---------- 格式化修饰符 ----------
        private String formatModifiers(int mods) {
            return Modifier.toString(mods);
        }
    }

    @Override
    public void stop() {}
}
