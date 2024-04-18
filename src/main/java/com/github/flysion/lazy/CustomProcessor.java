package com.github.flysion.lazy;

import com.github.flysion.lazy.annotation.Once;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.tools.Diagnostic;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Set;

@SupportedAnnotationTypes({"com.github.flysion.lazy.annotation.Once"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class CustomProcessor extends AbstractProcessor {

    private JavacTrees trees;

    private TreeMaker treeMaker;

    private Names names;

    private Messager messager;

    /**
     * 获取 JavacProcessingEnvironment
     * 编译器为了实现自己的一些能力，对 ProcessingEnvironment 进行了代理，此处获取实际的 ProcessingEnvironment
     *
     * @param procEnv ProcessingEnvironment
     * @return JavacProcessingEnvironment
     */
    private static ProcessingEnvironment tryRecursivelyObtainJavacProcEnv(ProcessingEnvironment procEnv) {
        if (procEnv.getClass().getName().equals("com.sun.tools.javac.processing.JavacProcessingEnvironment")) {
            return procEnv;
        }

        for (Class<?> procEnvClass = procEnv.getClass(); procEnvClass != null;
             procEnvClass = procEnvClass.getSuperclass()) {
            try {
                Object delegate = tryGetDelegateField(procEnvClass, procEnv);
                if (delegate == null) {
                    delegate = tryGetProcessingEnvField(procEnvClass, procEnv);
                }
                if (delegate == null) {
                    delegate = tryGetProxyDelegateToField(procEnvClass, procEnv);
                }
                if (delegate != null) {
                    return tryRecursivelyObtainJavacProcEnv((ProcessingEnvironment) delegate);
                }
            } catch (final Exception e) {
                // no valid delegate, try superclass
            }
        }

        return null;
    }

    /**
     * Gradle incremental processing
     *
     * @param delegateClass ProcessingEnvironment.class
     * @param instance      ProcessingEnvironment
     * @return
     */
    private static Object tryGetDelegateField(Class<?> delegateClass, Object instance) {
        try {
            return getField(delegateClass, "delegate").get(instance);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Kotlin incremental processing
     *
     * @param delegateClass ProcessingEnvironment.class
     * @param instance      ProcessingEnvironment
     * @return
     */
    private static Object tryGetProcessingEnvField(Class<?> delegateClass, Object instance) {
        try {
            return getField(delegateClass, "processingEnv").get(instance);
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * InteliJ >= 2020.3
     *
     * @param delegateClass ProcessingEnvironment.class
     * @param instance      ProcessingEnvironment
     * @return
     */
    private static Object tryGetProxyDelegateToField(Class<?> delegateClass, Object instance) {
        try {
            InvocationHandler handler = Proxy.getInvocationHandler(instance);
            return getField(handler.getClass(), "val$delegateTo").get(handler);
        } catch (Exception e) {
            return null;
        }
    }

    public static Field getField(Class<?> cls, String fieldName) throws NoSuchFieldException {
        Field field = null;
        Class<?> cls2 = cls;
        while (cls != null) {
            try {
                field = cls.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {

            }
            cls = cls.getSuperclass();
        }

        if (field == null) {
            throw new NoSuchFieldException(cls2.getName() + " :: " + fieldName);
        }

        field.setAccessible(true);
        return field;
    }

    @Override
    public synchronized void init(ProcessingEnvironment procEnv) {
        super.init(procEnv);

        final ProcessingEnvironment javacProcEnv = tryRecursivelyObtainJavacProcEnv(procEnv);
        if (javacProcEnv == null) {
            procEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("\"%s\" not found: %s",
                    JavacProcessingEnvironment.class.getName(), procEnv.getClass().getName()));
            return;
        }
        final Context context = ((JavacProcessingEnvironment) javacProcEnv).getContext();

        trees = JavacTrees.instance(javacProcEnv);
        treeMaker = TreeMaker.instance(context);
        names = Names.instance(context);
        messager = procEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        final Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Once.class);
        for (Element element : elements) {
            final javax.lang.model.element.Name methodName = element.getSimpleName();
            final ExecutableType elementType = (ExecutableType) element.asType();
            final Type returnType = (Type) elementType.getReturnType();
            final TypeElement classElement = (TypeElement) element.getEnclosingElement();
            final JCTree.JCClassDecl tree = trees.getTree(classElement);

            // 新增导入
            // 不能将自己的import放在defs[0],因为那里是package
            // 不能将自己的import放在defs最后，因为那里是public class
            final JCTree.JCCompilationUnit compilationUnit = (JCTree.JCCompilationUnit)
                    trees.getPath(element).getCompilationUnit();
            final JCTree.JCImport supplierImport = treeMaker.Import(
                    treeMaker.Select(
                            treeMaker.Ident(names.fromString("java.util.function")),
                            names.fromString("Supplier")
                    ),
                    false
            );
            if (!compilationUnit.defs.contains(supplierImport)) {
                ListBuffer<JCTree> imports = new ListBuffer<>();
                imports.add(compilationUnit.defs.get(0));
                imports.add(supplierImport);
                for (int i = 1; i < compilationUnit.defs.size(); i++) {
                    imports.add(compilationUnit.defs.get(i));
                }
                compilationUnit.defs = imports.toList();
            }

            // 如果方法是静态的属性就也是静态的
            int modifierFlags = Flags.PRIVATE | Flags.TRANSIENT;
            if (element.getModifiers().contains(Modifier.STATIC)) {
                modifierFlags |= Flags.STATIC;
            }
            final JCTree.JCModifiers modifiers = treeMaker.Modifiers(modifierFlags);
            // 定义属性用于存储方法返回值（方法返回值类型不能是void）
            final Name resultPropertyName = names.fromString("__" + methodName + "Result");
            final JCTree.JCExpression resultPropertyType = treeMaker.Type(returnType);
            if (returnType.getTag() != TypeTag.VOID) {
                final JCTree.JCVariableDecl resultPropertyVar = treeMaker.VarDef(modifiers, resultPropertyName,
                        resultPropertyType, null);
                tree.defs = tree.defs.prepend(resultPropertyVar);
            }
            // 定义属性用于存储方法是否调用
            final Name resultedPropertyName = names.fromString("__" + methodName + "Resulted");
            final JCTree.JCExpression resultedPropertyType = treeMaker.Ident(names.fromString("Boolean"));
            final JCTree.JCVariableDecl resultedPropertyVar = treeMaker.VarDef(modifiers, resultedPropertyName,
                    resultedPropertyType, treeMaker.Literal(Boolean.FALSE));
            tree.defs = tree.defs.prepend(resultedPropertyVar);

            // 修改原方法
            tree.accept(new TreeTranslator() {
                @Override
                public void visitMethodDef(JCTree.JCMethodDecl methodDecl) {
                    if (methodDecl.getName().toString().equals(methodName.toString())) {
                        // if条件成立语句块
                        ListBuffer<JCTree.JCStatement> ifStatements = new ListBuffer<>();
                        JCTree.JCIdent resultProperty = null;

                        if (returnType.getTag() != TypeTag.VOID) {
                            resultProperty = treeMaker.Ident(resultPropertyName);

                            // 将方法的body转为闭包
                            final Name lambdaName = names.fromString("__");
                            final JCTree.JCVariableDecl lambdaVar = treeMaker.VarDef(
                                    treeMaker.Modifiers(Flags.PARAMETER),
                                    lambdaName,
                                    treeMaker.Ident(names.fromString("Supplier")),
                                    treeMaker.Lambda(List.nil(), methodDecl.getBody())
                            );
                            ifStatements.add(lambdaVar);

                            // 调用闭包
                            final JCTree.JCFieldAccess lambdaGet = treeMaker.Select(
                                    treeMaker.Ident(lambdaName),
                                    names.fromString("get")
                            );
                            final JCTree.JCMethodInvocation lambdaApply = treeMaker.Apply(
                                    List.nil(), lambdaGet, List.nil()
                            );
                            final JCTree.JCExpressionStatement resultPropertyAssign = treeMaker.Exec(
                                    treeMaker.Assign(
                                            resultProperty,
                                            treeMaker.TypeCast(resultPropertyType, lambdaApply)
                                    )
                            );
                            ifStatements.add(resultPropertyAssign);
                        } else {
                            ifStatements.add(methodDecl.getBody());
                        }

                        // 标记方法已被调用
                        final JCTree.JCIdent resultedProperty = treeMaker.Ident(resultedPropertyName);
                        final JCTree.JCExpressionStatement resultedPropertyAssign = treeMaker.Exec(
                                treeMaker.Assign(resultedProperty, treeMaker.Literal(Boolean.TRUE))
                        );
                        ifStatements.add(resultedPropertyAssign);

                        // if
                        final JCTree.JCBlock ifBlock = treeMaker.Block(0, ifStatements.toList());
                        final JCTree.JCBinary ifCond = treeMaker.Binary(
                                JCTree.Tag.OR,
                                treeMaker.Binary(
                                        JCTree.Tag.EQ,
                                        treeMaker.Ident(resultedPropertyName),
                                        treeMaker.Literal(TypeTag.BOT, null)
                                ),
                                treeMaker.Binary(
                                        JCTree.Tag.EQ,
                                        treeMaker.Ident(resultedPropertyName),
                                        treeMaker.Literal(Boolean.FALSE)
                                )
                        );

                        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
                        statements.add(treeMaker.If(ifCond, ifBlock, null));
                        if (returnType.getTag() != TypeTag.VOID) {
                            statements.add(treeMaker.Return(resultProperty));
                        }
                        methodDecl.body = treeMaker.Block(0, statements.toList());
                        // this.result = treeMaker.MethodDef(methodDecl.sym, methodBlock);
                    }

                    super.visitMethodDef(methodDecl);
                }
            });
        }

        // 返回false 表示 当前处理器处理了之后 其他的处理器也可以接着处理
        // 返回true表示，我处理完了之后其他处理器不再处理
        return false;
    }
}
