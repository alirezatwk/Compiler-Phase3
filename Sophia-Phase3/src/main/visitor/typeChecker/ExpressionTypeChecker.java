package main.visitor.typeChecker;

import main.ast.nodes.declaration.classDec.ClassDeclaration;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.expression.operators.UnaryOperator;
import main.ast.nodes.expression.values.ListValue;
import main.ast.nodes.expression.values.NullValue;
import main.ast.nodes.expression.values.primitive.BoolValue;
import main.ast.nodes.expression.values.primitive.IntValue;
import main.ast.nodes.expression.values.primitive.StringValue;
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
import main.symbolTable.items.FieldSymbolTableItem;
import main.symbolTable.items.LocalVariableSymbolTableItem;
import main.symbolTable.items.MethodSymbolTableItem;
import main.symbolTable.utils.graph.Graph;
import main.visitor.Visitor;

import java.lang.reflect.Method;
import java.util.ArrayList;


public class ExpressionTypeChecker extends Visitor<Type> {
    private final Graph<String> classHierarchy;
    public static ClassDeclaration currentClass;

    public ExpressionTypeChecker(Graph<String> classHierarchy) {
        this.classHierarchy = classHierarchy;
    }

    @Override
    public Type visit(BinaryExpression binaryExpression) {
        Type firstOperandType = binaryExpression.getFirstOperand().accept(this);
        Type secondOperandType = binaryExpression.getSecondOperand().accept(this);
        if ((firstOperandType instanceof NoType) && (secondOperandType instanceof NoType)) {
            return new NoType();
        }
        if (binaryExpression.getBinaryOperator().equals(BinaryOperator.gt) ||
                binaryExpression.getBinaryOperator().equals(BinaryOperator.lt) ||
                binaryExpression.getBinaryOperator().equals(BinaryOperator.add) ||
                binaryExpression.getBinaryOperator().equals(BinaryOperator.sub) ||
                binaryExpression.getBinaryOperator().equals(BinaryOperator.mult) ||
                binaryExpression.getBinaryOperator().equals(BinaryOperator.div) ||
                binaryExpression.getBinaryOperator().equals(BinaryOperator.mod)) {
            if (firstOperandType instanceof IntType && secondOperandType instanceof IntType)
                return new IntType();
            if ((firstOperandType instanceof IntType || firstOperandType instanceof NoType) &&
                    (secondOperandType instanceof IntType || secondOperandType instanceof NoType))
                return new NoType();
        } else if (binaryExpression.getBinaryOperator().equals(BinaryOperator.and) ||
                binaryExpression.getBinaryOperator().equals(BinaryOperator.or)) {
            if (firstOperandType instanceof BoolType && secondOperandType instanceof BoolType)
                return new BoolType();
            if ((firstOperandType instanceof BoolType || firstOperandType instanceof NoType) &&
                    (secondOperandType instanceof BoolType || secondOperandType instanceof NoType))
                return new NoType();
        } else if (binaryExpression.getBinaryOperator().equals(BinaryOperator.eq) ||
                binaryExpression.getBinaryOperator().equals(BinaryOperator.neq)) {
            return new NoType();
            // TODO
        }
        binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(), binaryExpression.getBinaryOperator().name()));
        return new NoType();
    }

    @Override
    public Type visit(UnaryExpression unaryExpression) {
        Type expressionType = unaryExpression.getOperand().accept(this);
        if (expressionType instanceof NoType)
            return new NoType();
        if (!unaryExpression.getOperator().equals(UnaryOperator.not)) {
            if (expressionType instanceof IntType)
                return new IntType();
            else
                unaryExpression.addError(new UnsupportedOperandType(unaryExpression.getLine(), unaryExpression.getOperator().name()));
        } else {
            if (expressionType instanceof BoolType)
                return new BoolType();
            else
                unaryExpression.addError(new UnsupportedOperandType(unaryExpression.getLine(), unaryExpression.getOperator().name()));
        }
        return new NoType();
    }

    @Override
    public Type visit(ObjectOrListMemberAccess objectOrListMemberAccess) {
        Type instanceType = objectOrListMemberAccess.getInstance().accept(this);
        if (instanceType instanceof NoType) {
            return new NoType();
        }
        return new NoType();
    }

    @Override
    public Type visit(Identifier identifier) {
        int counter = 0;
        try {
            ClassSymbolTableItem classSymbolTableItem = (ClassSymbolTableItem) SymbolTable.top.getItem(ClassSymbolTableItem.START_KEY + identifier.getName(), true);
            return new ClassType(classSymbolTableItem.getClassDeclaration().getClassName());
        } catch (ItemNotFoundException itemNotFoundException) {
            counter += 1;
        }

        try {
            FieldSymbolTableItem fieldSymbolTableItem = (FieldSymbolTableItem) SymbolTable.top.getItem(FieldSymbolTableItem.START_KEY + identifier.getName(), true);
            return fieldSymbolTableItem.getType();
        } catch (ItemNotFoundException itemNotFoundException) {
            counter += 1;
        }

        try {
            LocalVariableSymbolTableItem localVariableSymbolTableItem = (LocalVariableSymbolTableItem) SymbolTable.top.getItem(LocalVariableSymbolTableItem.START_KEY + identifier.getName(), true);
            return localVariableSymbolTableItem.getType();
        } catch (ItemNotFoundException itemNotFoundException) {
            counter += 1;
        }

        try {
            MethodSymbolTableItem methodSymbolTableItem = (MethodSymbolTableItem) SymbolTable.top.getItem(MethodSymbolTableItem.START_KEY + identifier.getName(), true);
            return new FptrType(methodSymbolTableItem.getArgTypes(), methodSymbolTableItem.getReturnType());
        } catch (ItemNotFoundException itemNotFoundException) {
            counter += 1;
        }
        // TODO: Error 1
        return null;
    }

    @Override
    public Type visit(ListAccessByIndex listAccessByIndex) {
        Type indexType = listAccessByIndex.getIndex().accept(this);
        Type instanceType = listAccessByIndex.getInstance().accept(this);
        if (indexType instanceof NoType || instanceType instanceof NoType)
            return new NoType();

        boolean wasNotInt = false;
        if (!(indexType instanceof IntType)) {
            listAccessByIndex.addError(new ListIndexNotInt(listAccessByIndex.getLine()));
            wasNotInt = true;
        }
        if (!(instanceType instanceof ListType)) {
            listAccessByIndex.addError(new ListAccessByIndexOnNoneList(listAccessByIndex.getLine()));
            return new NoType();
        }

        boolean same = true;
        Type first = ((ListType) instanceType).getElementsTypes().get(0).getType();
        for (ListNameType listNameType : ((ListType) instanceType).getElementsTypes())
            if (!isSame(first, listNameType.getType())) {
                same = false;
                break;
            }
        if (!same && !(indexType instanceof IntType)) {
            listAccessByIndex.addError(new CantUseExprAsIndexOfMultiTypeList(listAccessByIndex.getLine()));
            return new NoType();
        } else if (wasNotInt) {
            return new NoType();
        } else {
            Expression index = listAccessByIndex.getIndex();
            if (same && index instanceof IntValue) {
                if (((IntValue) index).getConstant() < ((ListType) instanceType).getElementsTypes().size())
                    return ((ListType) instanceType).getElementsTypes().get(((IntValue) index).getConstant()).getType();
            } else {
                return ((ListType) instanceType).getElementsTypes().get(0).getType();
            }
        }
        return ((ListType) instanceType).getElementsTypes().get(0).getType();
    }

    @Override
    public Type visit(MethodCall methodCall) {
        Type instanceType = methodCall.getInstance().accept(this);
        if (instanceType instanceof NoType)
            return new NoType();
        if (!(instanceType instanceof FptrType)) {
            methodCall.addError(new CallOnNoneFptrType(methodCall.getLine()));
            return new NoType();
        }
        if (((FptrType) instanceType).getArgumentsTypes().size() != methodCall.getArgs().size()) {
            methodCall.addError(new MethodCallNotMatchDefinition(methodCall.getLine()));
            return new NoType();
        }
        for (int i = 0; i < methodCall.getArgs().size(); i += 1) {
            Type argType = methodCall.getArgs().get(i).accept(this);
            if (!isSubType(argType, ((FptrType) instanceType).getArgumentsTypes().get(i))) {
                methodCall.addError(new MethodCallNotMatchDefinition(methodCall.getLine()));
                return new NoType();
            }
        }
        return ((FptrType) instanceType).getReturnType();
    }

    @Override
    public Type visit(NewClassInstance newClassInstance) {
        // new A(3, 2)
        try {
            SymbolTable.top.getItem(ClassSymbolTableItem.START_KEY + newClassInstance.getClassType().getClassName().getName(), true);

            // TODO : Check arguments types
        } catch (ItemNotFoundException itemNotFoundException) {
            newClassInstance.addError(new ClassNotDeclared(newClassInstance.getLine(), currentClass.getClassName().getName()));
            return new NoType();
        }
        return newClassInstance.getClassType();
    }

    @Override
    public Type visit(ThisClass thisClass) {
        return new ClassType(currentClass.getClassName());
    }

    @Override
    public Type visit(ListValue listValue) {
        ListType listType = new ListType();
        for (Expression expression : listValue.getElements()) {
            Type expressionType = expression.accept(this);
            ListNameType listElementType = new ListNameType(expressionType);
            listType.addElementType(listElementType);
        }
        return listType;
    }

    @Override
    public Type visit(NullValue nullValue) {
        return new NullType();
    }

    @Override
    public Type visit(IntValue intValue) {
        return new IntType();
    }

    @Override
    public Type visit(BoolValue boolValue) {
        return new BoolType();
    }

    @Override
    public Type visit(StringValue stringValue) {
        return new StringType();
    }

    public boolean isSubType(Type a, Type b) {
        if (a instanceof NoType)
            return true;
        if (a instanceof BoolType && b instanceof BoolType)
            return true;
        if (a instanceof IntType && b instanceof IntType)
            return true;
        if (a instanceof StringType && b instanceof StringType)
            return true;
        if (a instanceof ClassType && b instanceof ClassType) {
            if (((ClassType) a).getClassName().getName().equals(((ClassType) b).getClassName().getName()))
                return true;
            if (classHierarchy.isSecondNodeAncestorOf(((ClassType) a).getClassName().getName(), ((ClassType) b).getClassName().getName()))
                return true;
            return false;
        }
        if (a instanceof ListType && b instanceof ListType) {
            ArrayList<ListNameType> aList = ((ListType) a).getElementsTypes();
            ArrayList<ListNameType> bList = ((ListType) b).getElementsTypes();
            if (aList.size() != bList.size())
                return false;
            for (int i = 0; i < aList.size(); i += 1)
                if (!isSubType(aList.get(i).getType(), bList.get(i).getType()))
                    return false;
            return true;
        }
        if (a instanceof FptrType && b instanceof FptrType) {
            if (!isSubType(((FptrType) a).getReturnType(), ((FptrType) b).getReturnType()))
                return false;
            ArrayList<Type> aList = ((FptrType) a).getArgumentsTypes();
            ArrayList<Type> bList = ((FptrType) b).getArgumentsTypes();
            if (aList.size() != bList.size())
                return false;
            for (int i = 0; i < aList.size(); i += 1)
                if (!isSubType(bList.get(i), aList.get(i)))
                    return false;
            return true;
        }
        if (a instanceof NullType && (b instanceof ClassType || b instanceof FptrType))
            return true;
        return false;
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
        if (a instanceof NullType && b instanceof NullType)
            return true;
        return false;
    }
}
