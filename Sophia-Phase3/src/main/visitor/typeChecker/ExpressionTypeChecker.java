package main.visitor.typeChecker;

import main.ast.nodes.declaration.classDec.ClassDeclaration;
import main.ast.nodes.expression.*;
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
import main.compileErrorException.typeErrors.CallOnNoneFptrType;
import main.compileErrorException.typeErrors.ClassNotDeclared;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.ItemNotFoundException;
import main.symbolTable.items.ClassSymbolTableItem;
import main.symbolTable.items.FieldSymbolTableItem;
import main.symbolTable.items.LocalVariableSymbolTableItem;
import main.symbolTable.items.MethodSymbolTableItem;
import main.symbolTable.utils.graph.Graph;
import main.visitor.Visitor;


public class ExpressionTypeChecker extends Visitor<Type> {
    private final Graph<String> classHierarchy;
    public static ClassDeclaration currentClass;

    public ExpressionTypeChecker(Graph<String> classHierarchy) {
        this.classHierarchy = classHierarchy;
    }

    @Override
    public Type visit(BinaryExpression binaryExpression) {
        //TODO
        return null;
    }

    @Override
    public Type visit(UnaryExpression unaryExpression) {
        //TODO
        return null;
    }

    @Override
    public Type visit(ObjectOrListMemberAccess objectOrListMemberAccess) {
        //TODO
        return null;
    }

    @Override
    public Type visit(Identifier identifier) {
        try {
            SymbolTable.top.getItem(ClassSymbolTableItem.START_KEY + identifier.getName(), true);
        }
        catch (ItemNotFoundException ignored) {}

        try {
            SymbolTable.top.getItem(FieldSymbolTableItem.START_KEY + identifier.getName(), true);
        }
        catch (ItemNotFoundException ignored) {}

        try {
            SymbolTable.top.getItem(LocalVariableSymbolTableItem.START_KEY + identifier.getName(), true);
        }
        catch (ItemNotFoundException ignored) {}

        try {
            SymbolTable.top.getItem(MethodSymbolTableItem.START_KEY + identifier.getName(), true);
        }
        catch (ItemNotFoundException ignored) {}

        return null;
    }

    @Override
    public Type visit(ListAccessByIndex listAccessByIndex) {
        //TODO
        return null;
    }

    @Override
    public Type visit(MethodCall methodCall) {
        //TODO

        // Error 8
        if(!(methodExpressionType instanceof FptrType)) {
            methodCallStmt.addError(new CallOnNoneFptrType(methodCallStmt.getLine()));
            return null;
        }
        return null;
    }

    @Override
    public Type visit(NewClassInstance newClassInstance) {
        // new A(3, 2)
        try {
            SymbolTable.top.getItem(ClassSymbolTableItem.START_KEY + newClassInstance.getClass().getName(), true);
            // TODO : Check arguments types
        }
        catch (ItemNotFoundException itemNotFoundException){
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
}
