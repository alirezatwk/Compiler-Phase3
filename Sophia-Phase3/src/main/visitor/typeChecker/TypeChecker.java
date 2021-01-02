package main.visitor.typeChecker;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.classDec.ClassDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.ConstructorDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.FieldDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.MethodDeclaration;
import main.ast.nodes.declaration.variableDec.VarDeclaration;
import main.ast.nodes.expression.MethodCall;
import main.ast.nodes.statement.*;
import main.ast.nodes.statement.loop.BreakStmt;
import main.ast.nodes.statement.loop.ContinueStmt;
import main.ast.nodes.statement.loop.ForStmt;
import main.ast.nodes.statement.loop.ForeachStmt;
import main.ast.types.NoType;
import main.ast.types.NullType;
import main.ast.types.Type;
import main.ast.types.functionPointer.FptrType;
import main.ast.types.list.ListNameType;
import main.ast.types.list.ListType;
import main.ast.types.single.BoolType;
import main.ast.types.single.ClassType;
import main.ast.types.single.IntType;
import main.ast.types.single.StringType;
import main.compileErrorException.typeErrors.*;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.ItemNotFoundException;
import main.symbolTable.items.ClassSymbolTableItem;
import main.symbolTable.items.MethodSymbolTableItem;
import main.symbolTable.utils.graph.Graph;
import main.symbolTable.utils.graph.exceptions.GraphDoesNotContainNodeException;
import main.visitor.Visitor;

import javax.swing.plaf.nimbus.State;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TypeChecker extends Visitor<Void> {
    private final Graph<String> classHierarchy;
    private final ExpressionTypeChecker expressionTypeChecker;
    public static MethodDeclaration currentMethod;
    public static int loopCnt;

    public TypeChecker(Graph<String> classHierarchy) {
        this.classHierarchy = classHierarchy;
        this.expressionTypeChecker = new ExpressionTypeChecker(classHierarchy);
        ExpressionTypeChecker.currentClass = null;
        currentMethod = null;
        loopCnt = 0;
    }

    @Override
    public Void visit(Program program) {
        SymbolTable.top = SymbolTable.root;
        boolean hasMain = false;
        for (ClassDeclaration classDeclaration : program.getClasses()) {
            if (classDeclaration.getClassName().getName().equals("Main"))
                hasMain = true;
            classDeclaration.accept(this);
        }
        // Error 25
        if (!hasMain)
            program.addError(new NoMainClass());
        return null;
    }

    @Override
    public Void visit(ClassDeclaration classDeclaration) {
        try {
            ExpressionTypeChecker.currentClass = classDeclaration;
            ClassSymbolTableItem classSymbolTableItem = (ClassSymbolTableItem) SymbolTable.top.getItem(ClassSymbolTableItem.START_KEY + classDeclaration.getClassName().getName(), true);
            SymbolTable.push(classSymbolTableItem.getClassSymbolTable());
        } catch (ItemNotFoundException ignored) {
        }

        // Main
        if (classDeclaration.getClassName().getName().equals("Main")) {
            try {
                Collection<String> collection = classHierarchy.getParentsOfNode(classDeclaration.getClassName().getName());
                // Error 26
                if (!collection.isEmpty())
                    classDeclaration.addError(new MainClassCantExtend(classDeclaration.getLine()));

                // Error 28
                if (classDeclaration.getConstructor() == null)
                    classDeclaration.addError(new NoConstructorInMainClass(classDeclaration));

            } catch (GraphDoesNotContainNodeException ignored) {
            } // Cause it can't.
        } else {
            // Other
            // Error 27
            if (classDeclaration.getParentClassName() != null && classDeclaration.getParentClassName().getName().equals("Main"))
                classDeclaration.addError(new CannotExtendFromMainClass(classDeclaration.getLine()));
        }
        if (classDeclaration.getConstructor() != null)
            classDeclaration.getConstructor().accept(this);
        for (MethodDeclaration methodDeclaration : classDeclaration.getMethods()) {
            currentMethod = methodDeclaration;
            methodDeclaration.accept(this);
        }
        for (FieldDeclaration fieldDeclaration : classDeclaration.getFields())
            fieldDeclaration.accept(this);

        SymbolTable.pop();
        return null;
    }

    @Override
    public Void visit(ConstructorDeclaration constructorDeclaration) {
        try {
            MethodSymbolTableItem methodSymbolTableItem = (MethodSymbolTableItem) SymbolTable.top.getItem(MethodSymbolTableItem.START_KEY + constructorDeclaration.getMethodName().getName(), true);
            SymbolTable.push(methodSymbolTableItem.getMethodSymbolTable());
        } catch (ItemNotFoundException ignored) {
        }

        // Error 29
        if (ExpressionTypeChecker.currentClass.getClassName().getName().equals("Main")) {
            if (!constructorDeclaration.getArgs().isEmpty()) {
                constructorDeclaration.addError(new MainConstructorCantHaveArgs(constructorDeclaration.getLine()));
            }
        }
        // Error 17
        if(!ExpressionTypeChecker.currentClass.getClassName().getName().equals(constructorDeclaration.getMethodName().getName()))
            constructorDeclaration.addError(new ConstructorNotSameNameAsClass(constructorDeclaration.getLine()));

        for (VarDeclaration varDeclaration : constructorDeclaration.getArgs())
            varDeclaration.accept(this);
        for (VarDeclaration varDeclaration : constructorDeclaration.getLocalVars())
            varDeclaration.accept(this);
        for (Statement statement : constructorDeclaration.getBody())
            statement.accept(this);
        SymbolTable.pop();
        return null;
    }

    @Override
    public Void visit(MethodDeclaration methodDeclaration) {
        try {
            MethodSymbolTableItem methodSymbolTableItem = (MethodSymbolTableItem) SymbolTable.top.getItem(MethodSymbolTableItem.START_KEY + methodDeclaration.getMethodName().getName(), true);
            SymbolTable.push(methodSymbolTableItem.getMethodSymbolTable());
        } catch (ItemNotFoundException ignored) {
        }

        for (VarDeclaration varDeclaration : methodDeclaration.getArgs())
            varDeclaration.accept(this);
        for (VarDeclaration varDeclaration : methodDeclaration.getLocalVars())
            varDeclaration.accept(this);
        for (Statement statement : methodDeclaration.getBody())
            statement.accept(this);
        SymbolTable.pop();
        return null;
    }

    @Override
    public Void visit(FieldDeclaration fieldDeclaration) {
        fieldDeclaration.getVarDeclaration().accept(this);
        return null;
    }

    @Override
    public Void visit(VarDeclaration varDeclaration) {
        Type identifierType = varDeclaration.getType();
        if (identifierType instanceof ListType) {
            ListType listType = (ListType) identifierType;
            // Error 11
            if (listType.getElementsTypes().isEmpty())
                varDeclaration.addError(new CannotHaveEmptyList(varDeclaration.getLine()));

        }
        return null;
    }

    @Override
    public Void visit(AssignmentStmt assignmentStmt) {
        Type lType = assignmentStmt.getlValue().accept(this.expressionTypeChecker);
        Type rType = assignmentStmt.getrValue().accept(this.expressionTypeChecker);
        // TODO: Errors
        return null;
    }

    @Override
    public Void visit(BlockStmt blockStmt) {
        for (Statement statement : blockStmt.getStatements())
            statement.accept(this);
        return null;
    }

    @Override
    public Void visit(ConditionalStmt conditionalStmt) {
        // Error 5
        if (conditionalStmt.getCondition() != null) {
            Type conditionType = conditionalStmt.getCondition().accept(this.expressionTypeChecker);
            if (!(conditionType instanceof BoolType) && !(conditionType instanceof NoType))
                conditionalStmt.addError(new ConditionNotBool(conditionalStmt.getLine()));
        } else
            conditionalStmt.addError(new ConditionNotBool(conditionalStmt.getLine()));

        conditionalStmt.getThenBody().accept(this);
        if (conditionalStmt.getElseBody() != null)
            conditionalStmt.getElseBody().accept(this);
        return null;
    }

    @Override
    public Void visit(MethodCallStmt methodCallStmt) {
        methodCallStmt.getMethodCall().accept(this.expressionTypeChecker);
        return null;
    }

    // TODO: LocalVariableSymbolTableItem????

    @Override
    public Void visit(PrintStmt print) {
        Type argType = print.getArg().accept(this.expressionTypeChecker);
        if (!(argType instanceof IntType) && !(argType instanceof BoolType) && !(argType instanceof StringType))
            print.addError(new UnsupportedTypeForPrint(print.getLine()));
        return null;
    }

    @Override
    public Void visit(ReturnStmt returnStmt) {
        Type returnType = returnStmt.getReturnedExpr().accept(this.expressionTypeChecker);
        try {
            MethodSymbolTableItem methodSymbolTableItem = (MethodSymbolTableItem) SymbolTable.top.getItem(MethodSymbolTableItem.START_KEY + currentMethod.getMethodName().getName(), true);
            // Error 14
            if (!isSame(returnType, methodSymbolTableItem.getReturnType()))
                returnStmt.addError(new ReturnValueNotMatchMethodReturnType(returnStmt));
        } catch (ItemNotFoundException ignored) {
        }
        return null;
    }

    @Override
    public Void visit(BreakStmt breakStmt) {
        if (loopCnt <= 0)
            breakStmt.addError(new ContinueBreakNotInLoop(breakStmt.getLine(), 0));
        return null;
    }

    @Override
    public Void visit(ContinueStmt continueStmt) {
        if (loopCnt <= 0)
            continueStmt.addError(new ContinueBreakNotInLoop(continueStmt.getLine(), 1));
        return null;
    }

    @Override
    public Void visit(ForeachStmt foreachStmt) {
        Type identifierType = foreachStmt.getVariable().accept(this.expressionTypeChecker);
        Type expressionType = foreachStmt.getList().accept(this.expressionTypeChecker);

        // Error 19
        if (!(expressionType instanceof ListType) && !(expressionType instanceof NoType))
            foreachStmt.addError(new ForeachCantIterateNoneList(foreachStmt.getLine()));
        else if (expressionType instanceof ListType) {
            boolean allSame = true;
            ListType listType = (ListType) expressionType;
            Type firstType = listType.getElementsTypes().get(0).getType();
            for (ListNameType listNameType : listType.getElementsTypes())
                if (!isSame(firstType, listNameType.getType())) {
                    allSame = false;
                    break;
                }

            // Error 20
            if (!allSame)
                foreachStmt.addError(new ForeachListElementsNotSameType(foreachStmt.getLine()));
            // Error 21
            if (!isSame(firstType, identifierType))
                foreachStmt.addError(new ForeachVarNotMatchList(foreachStmt));
        }
        loopCnt += 1;
        foreachStmt.getBody().accept(this);
        loopCnt -= 1;
        return null;
    }

    @Override
    public Void visit(ForStmt forStmt) {
        if (forStmt.getInitialize() != null)
            forStmt.getInitialize().accept(this);
        if (forStmt.getCondition() != null) {
            Type conditionType = forStmt.getCondition().accept(this.expressionTypeChecker);
            // Error 5
            if (!(conditionType instanceof BoolType))
                forStmt.addError(new ConditionNotBool(forStmt.getLine()));
        }
        if (forStmt.getUpdate() != null)
            forStmt.getUpdate().accept(this);
        loopCnt += 1;
        forStmt.getBody().accept(this);
        loopCnt -= 1;
        return null;
    }

    public boolean isSame(Type a, Type b) {
        if (a instanceof NoType || b instanceof NoType)
            return true;
        if (a instanceof BoolType && b instanceof BoolType)
            return true;
        if (a instanceof IntType && b instanceof IntType)
            return true;
        if (a instanceof StringType && b instanceof StringType)
            return true;
        if (a instanceof ClassType && b instanceof ClassType && ((ClassType) a).getClassName().getName().equals(((ClassType) b).getClassName().getName()))
            return true;
        if (a instanceof ListType && b instanceof ListType) {
            ArrayList<ListNameType> aList = ((ListType) a).getElementsTypes();
            ArrayList<ListNameType> bList = ((ListType) b).getElementsTypes();
            if (aList.size() != bList.size())
                return false;
            for (int i = 0; i < aList.size(); i += 1)
                if (!isSame(aList.get(i).getType(), bList.get(i).getType()))
                    return false;
            return true;
        }
        if (a instanceof FptrType && b instanceof FptrType) {
            if (!isSame(((FptrType) a).getReturnType(), ((FptrType) b).getReturnType()))
                return false;
            ArrayList<Type> aList = ((FptrType) a).getArgumentsTypes();
            ArrayList<Type> bList = ((FptrType) b).getArgumentsTypes();
            if (aList.size() != bList.size())
                return false;
            for (int i = 0; i < aList.size(); i += 1)
                if (!isSame(aList.get(i), bList.get(i)))
                    return false;
            return true;
        }
        if(a instanceof NullType && b instanceof NullType)
            return true;
        return false;
    }
}
