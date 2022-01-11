package main.visitor.codeGenerator;

import main.ast.nodes.declaration.*;
import main.ast.nodes.declaration.struct.*;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.*;
import main.ast.nodes.expression.values.primitive.*;
import main.ast.nodes.statement.*;
import main.ast.types.*;
import main.ast.types.primitives.*;
import main.symbolTable.exceptions.*;
import main.visitor.Visitor;
import main.visitor.type.ExpressionTypeChecker;

import java.io.*;
import java.util.*;

import main.ast.nodes.Program;
import main.ast.nodes.expression.Identifier;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.symbolTable.SymbolTable;
import main.symbolTable.items.*;

public class CodeGenerator extends Visitor<String> {
    ExpressionTypeChecker expressionTypeChecker = new ExpressionTypeChecker();
    private String outputPath;
    private FileWriter currentFile;
    private int numOfUsedLabel;
    private FunctionDeclaration currFunc;
    private int numOfUsedTemp;

    private StructDeclaration currStruct;
    private ArrayList<String> scopeVars = new ArrayList<>();

    private String stackLimit = "128";
    private String localLimit = "128";

    private void copyFile(String toBeCopied, String toBePasted) {
        try {
            this.numOfUsedLabel = 0;
            this.numOfUsedTemp = 0;
            File readingFile = new File(toBeCopied);
            File writingFile = new File(toBePasted);
            InputStream readingFileStream = new FileInputStream(readingFile);
            OutputStream writingFileStream = new FileOutputStream(writingFile);
            byte[] buffer = new byte[1024];
            int readLength;
            while ((readLength = readingFileStream.read(buffer)) > 0)
                writingFileStream.write(buffer, 0, readLength);
            readingFileStream.close();
            writingFileStream.close();
        } catch (IOException e) {//unreachable
        }
    }

    private void prepareOutputFolder() {
        this.outputPath = "output/";
        String jasminPath = "utilities/jarFiles/jasmin.jar";
        String listClassPath = "utilities/codeGenerationUtilityClasses/List.j";
        String fptrClassPath = "utilities/codeGenerationUtilityClasses/Fptr.j";
        try {
            File directory = new File(this.outputPath);
            File[] files = directory.listFiles();
            if (files != null)
                for (File file : files)
                    file.delete();
            directory.mkdir();
        } catch (SecurityException e) {//unreachable

        }
        copyFile(jasminPath, this.outputPath + "jasmin.jar");
        copyFile(listClassPath, this.outputPath + "List.j");
        copyFile(fptrClassPath, this.outputPath + "Fptr.j");
    }

    private void createFile(String name) {
        try {
            String path = this.outputPath + name + ".j";
            File file = new File(path);
            file.createNewFile();
            this.currentFile = new FileWriter(path);
        } catch (IOException e) {//never reached
        }
    }

    private void addCommand(String command) {
        try {
            command = String.join("\n\t\t", command.split("\n"));
            if (command.startsWith("Label_"))
                this.currentFile.write("\t" + command + "\n");
            else if (command.startsWith("."))
                this.currentFile.write(command + "\n");
            else
                this.currentFile.write("\t\t" + command + "\n");
            this.currentFile.flush();
        } catch (IOException e) {//unreachable

        }
    }

    private void addStaticMainMethod() {
        addCommand(".method public static main([Ljava/lang/String;)V");
        addCommand(".limit stack " + stackLimit);
        addCommand(".limit locals " + localLimit);
        addCommand("new Main");
        addCommand("invokespecial Main/<init>()V");
        addCommand("return");
        addCommand(".end method");
    }

    private String getFreshLabel() {
        String label = "Label_";
        label += numOfUsedLabel;
        numOfUsedLabel++;
        return label;
    }

    private String makeTypeSignature(Type t) {
        if (t instanceof IntType)
            return "java/lang/Integer";
        if (t instanceof BoolType)
            return "java/lang/Boolean";
        if (t instanceof ListType)
            return "List";
        if (t instanceof FptrType)
            return "Fptr";
        if (t instanceof StructType)
            return ((StructType) t).getStructName().getName();
        if (t instanceof VoidType)
            return "V";
        return null;
    }

    private int slotOf(String identifier) {
        if (identifier.equals("")) {
            int temp = numOfUsedTemp;
            numOfUsedTemp++;
            return scopeVars.size() - 1 + temp;
        }
        if (scopeVars.contains(identifier))
            return scopeVars.indexOf(identifier);
        return 0;
    }


    @Override
    public String visit(Program program) {
        prepareOutputFolder();
        for (StructDeclaration structDeclaration : program.getStructs()) {
            structDeclaration.accept(this);
        }
        for (FunctionDeclaration functionDeclaration : program.getFunctions()) {
            functionDeclaration.accept(this);
        }
        createFile("Main");
        program.getMain().accept(this);
        return null;
    }

    @Override
    public String visit(StructDeclaration structDeclaration) {
        try {
            String structKey = StructSymbolTableItem.START_KEY + structDeclaration.getStructName().getName();
            StructSymbolTableItem structSymbolTableItem = (StructSymbolTableItem) SymbolTable.root.getItem(structKey);
            SymbolTable.push(structSymbolTableItem.getStructSymbolTable());
        } catch (ItemNotFoundException e) {//unreachable
        }
        createFile(structDeclaration.getStructName().getName());
        addCommand(".class public " + structDeclaration.getStructName().getName());
        addCommand(".super java/lang/Object");
        scopeVars.add(structDeclaration.getStructName().getName());
        addDefaultConstructor();
        structDeclaration.getBody().accept(this);
        addCommand("return");
        addCommand(".end method");
        SymbolTable.pop();
        scopeVars.clear();
        return null;
    }

    @Override
    public String visit(FunctionDeclaration functionDeclaration) {
        try {
            String functionKey = FunctionSymbolTableItem.START_KEY + functionDeclaration.getFunctionName().getName();
            FunctionSymbolTableItem functionSymbolTableItem = (FunctionSymbolTableItem) SymbolTable.root.getItem(functionKey);
            SymbolTable.push(functionSymbolTableItem.getFunctionSymbolTable());
        } catch (ItemNotFoundException e) {//unreachable
        }
        StringBuilder command = new StringBuilder(".method public " + functionDeclaration.getFunctionName().getName() + "(");
        scopeVars.add(functionDeclaration.getFunctionName().getName());
        for (VariableDeclaration variableDeclaration : functionDeclaration.getArgs()) {
            String type = makeTypeSignature(variableDeclaration.getVarType());
            command.append(type).append(";");
            scopeVars.add(variableDeclaration.getVarName().getName());
        }
        command.append(")");
        String returnType = makeTypeSignature(functionDeclaration.getReturnType());
        command.append(returnType);
        addCommand(command.toString());
        functionDeclaration.getBody().accept(this);
        addCommand(".end method");
        scopeVars.clear();
        SymbolTable.pop();
        return null;
    }

    public void addDefaultConstructor() {
        addCommand(".method public <init>()V");
        addCommand(".limit stack " + stackLimit);
        addCommand(".limit locals " + localLimit);
        addCommand("aload_0");
        addCommand("invokespecial java/lang/Object/<init>()V");
    }

    @Override
    public String visit(MainDeclaration mainDeclaration) {
        FunctionSymbolTableItem mainFunc = null;
        try {
            String functionKey = FunctionSymbolTableItem.START_KEY + "main";
            mainFunc = (FunctionSymbolTableItem) SymbolTable.root.getItem(functionKey);
            SymbolTable.push(mainFunc.getFunctionSymbolTable());
        } catch (ItemNotFoundException e) {//unreachable
        }
        addCommand(".class public Main");
        addCommand(".super java/lang/Object");
        addStaticMainMethod();
        addDefaultConstructor();
        mainDeclaration.getBody().accept(this);
        addCommand("return");
        addCommand(".end method");
        scopeVars.clear();
        SymbolTable.pop();
        return null;
    }

    @Override
    public String visit(VariableDeclaration variableDeclaration) {
        scopeVars.add(variableDeclaration.getVarName().getName());
        if (variableDeclaration.getDefaultValue() != null)
            addCommand(variableDeclaration.getDefaultValue().accept(this));
        if (variableDeclaration.getVarType() instanceof IntType ||
                variableDeclaration.getVarType() instanceof BoolType)
            addCommand("iconst_0");
        else if (variableDeclaration.getVarType() instanceof FptrType)
            addCommand("aconst_null");
        else if (variableDeclaration.getVarType() instanceof StructType)
            addCommand("new " + ((StructType) variableDeclaration.getVarType()).getStructName().getName());
        else if (variableDeclaration.getVarType() instanceof ListType)
            addCommand("new List");
        if (variableDeclaration.getVarType() instanceof IntType ||
                variableDeclaration.getVarType() instanceof BoolType) {
            addCommand("istore" + getSlot(variableDeclaration.getVarName().getName()));
        } else {
            addCommand("astore" + getSlot(variableDeclaration.getVarName().getName()));
        }
        return null;
    }

    private String getSlot(String identifier) {
        int slot = slotOf(identifier);
        return slot <= 3 ? "_" + slot : " " + slot;
    }

    @Override
    public String visit(SetGetVarDeclaration setGetVarDeclaration) {
        return null;
    }

    @Override
    public String visit(AssignmentStmt assignmentStmt) {
        BinaryExpression assignExpr = new BinaryExpression(
                assignmentStmt.getLValue(),
                assignmentStmt.getRValue(),
                BinaryOperator.assign);
        addCommand(assignExpr.accept(this));
        //addCommand("pop");
        return null;
    }

    @Override
    public String visit(BlockStmt blockStmt) {
        for (Statement statement : blockStmt.getStatements()) {
            statement.accept(this);
        }
        return null;
    }

    @Override
    public String visit(ConditionalStmt conditionalStmt) {
        String labelFalse = getFreshLabel();
        String labelAfter = getFreshLabel();
        addCommand(conditionalStmt.getCondition().accept(this));
        addCommand("ifeq " + labelFalse);
        conditionalStmt.getThenBody().accept(this);
        addCommand("goto " + labelAfter);
        addCommand(labelFalse + ":");
        if (conditionalStmt.getElseBody() != null)
            conditionalStmt.getElseBody().accept(this);
        addCommand(labelAfter + ":");
        return null;
    }

    @Override
    public String visit(FunctionCallStmt functionCallStmt) {
        expressionTypeChecker.setInFunctionCallStmt(true);
        addCommand(functionCallStmt.getFunctionCall().accept(this));
        addCommand("pop");
        expressionTypeChecker.setInFunctionCallStmt(false);
        return null;
    }

    @Override
    public String visit(DisplayStmt displayStmt) {
        addCommand("getstatic java/lang/System/out Ljava/io/PrintStream;");
        Type argType = displayStmt.getArg().accept(expressionTypeChecker);
        String commandsOfArg = displayStmt.getArg().accept(this);

        addCommand(commandsOfArg);
        if (argType instanceof IntType)
            addCommand("invokevirtual java/io/PrintStream/println(I)V");
        if (argType instanceof BoolType)
            addCommand("invokevirtual java/io/PrintStream/println(Z)V");

        return null;
    }

    @Override
    public String visit(ReturnStmt returnStmt) {
        Type type = returnStmt.getReturnedExpr().accept(expressionTypeChecker);
        if (type instanceof VoidType) {
            addCommand("return");
        } else {
            addCommand(returnStmt.getReturnedExpr().accept(this));
            if (type instanceof IntType)
                addCommand("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
            if (type instanceof BoolType)
                addCommand("invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;");
            addCommand("areturn");
        }
        return null;
    }

    @Override
    public String visit(LoopStmt loopStmt) {
        loopStmt.getCondition().accept(this);
        int index = loopStmt.getLine();
        addCommand("istore " + index);
        int uniqueLabel = numOfUsedLabel++;
        addCommand("goto check" + uniqueLabel);
        addCommand("begin" + uniqueLabel + ":");
        loopStmt.getBody().accept(this);
        addCommand("iinc " + index + " -1");
        addCommand("check" + uniqueLabel + ":");
        addCommand("iload " + index);
        addCommand("ifgt begin" + uniqueLabel);
        return null;

    }

    @Override
    public String visit(VarDecStmt varDecStmt) {
        for (VariableDeclaration variableDeclaration : varDecStmt.getVars()) {
            variableDeclaration.accept(this);
        }
        return null;
    }

    @Override
    public String visit(ListAppendStmt listAppendStmt) {
        expressionTypeChecker.setInFunctionCallStmt(true);
        addCommand(listAppendStmt.getListAppendExpr().accept(this));
        expressionTypeChecker.setInFunctionCallStmt(false);
        return null;
    }

    @Override
    public String visit(ListSizeStmt listSizeStmt) {
        expressionTypeChecker.setInFunctionCallStmt(true);
        addCommand(listSizeStmt.getListSizeExpr().accept(this));
        expressionTypeChecker.setInFunctionCallStmt(false);
        return null;
    }

    public String getOperationCommand(BinaryOperator bo) {
        if (bo.equals(BinaryOperator.add))
            return "iadd\n";
        if (bo.equals(BinaryOperator.sub))
            return "isub\n";
        if (bo.equals(BinaryOperator.mult))
            return "imul\n";
        if (bo.equals(BinaryOperator.div))
            return "idiv\n";
        return null;
    }

    @Override
    public String visit(BinaryExpression binaryExpression) {
        String command = "";
        String commandLeft = binaryExpression.getFirstOperand().accept(this);
        String commandRight = binaryExpression.getSecondOperand().accept(this);
        Type tl = binaryExpression.getFirstOperand().accept(expressionTypeChecker);
        Type tr = binaryExpression.getSecondOperand().accept(expressionTypeChecker);
        BinaryOperator operator = binaryExpression.getBinaryOperator();
        if (operator.equals(BinaryOperator.add) ||
                operator.equals(BinaryOperator.sub) ||
                operator.equals(BinaryOperator.mult) ||
                operator.equals(BinaryOperator.div)) {
            command += commandLeft;
            command += "invokevirtual java/lang/Integer/intValue()I\n";
            command += commandRight;
            command += "invokevirtual java/lang/Integer/intValue()I\n";
            command += getOperationCommand(operator);
            command += "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n";
        }

        if (operator.equals(BinaryOperator.and) || operator.equals(BinaryOperator.or)) {
            String elseLabel = getFreshLabel();
            String afterLabel = getFreshLabel();
            String ifCommand = "";
            command += commandLeft;
            command += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
            if (operator.equals(BinaryOperator.and))
                ifCommand = "ifeq ";
            else
                ifCommand = "ifne ";
            command += ifCommand + elseLabel + "\n";
            command += commandRight;
            command += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
            command += "goto " + afterLabel + "\n";
            command += elseLabel + ":\n";
            command += "iconst_" + (operator.equals(BinaryOperator.or) ? "1" : "0") + "\n";
            command += afterLabel + ":\n";
            command += "invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;\n";
        }

        if (operator.equals(BinaryOperator.lt) || operator.equals(BinaryOperator.gt)) {
            String elseLabel = getFreshLabel();
            String afterLabel = getFreshLabel();
            String ifCommand = "";
            command += commandLeft;
            command += "invokevirtual java/lang/Integer/intValue()I\n";
            command += commandRight;
            command += "invokevirtual java/lang/Integer/intValue()I\n";
            if (operator.equals(BinaryOperator.lt))
                ifCommand = "if_icmpge";
            else
                ifCommand = "if_icmple";
            command += ifCommand + " " + elseLabel + "\n";
            command += "iconst_1\n";
            command += "goto " + afterLabel + "\n";
            command += elseLabel + ":\n";
            command += "iconst_0\n";
            command += afterLabel + ":\n";
            command += "invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;\n";
        }

        if (operator.equals(BinaryOperator.eq)) {
            String elseLabel = getFreshLabel();
            String afterLabel = getFreshLabel();
            String ifCommand = "";
            if (tl instanceof IntType) {
                command += commandLeft;
                command += "invokevirtual java/lang/Integer/intValue()I\n";
                command += commandRight;
                command += "invokevirtual java/lang/Integer/intValue()I\n";
                ifCommand = "if_icmpne";
            }
            if (tl instanceof BoolType) {
                command += commandLeft;
                command += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
                command += commandRight;
                command += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
                ifCommand = "if_icmpne";
            }
            if (tl instanceof ListType || tl instanceof FptrType) {
                command += commandLeft;
                command += commandRight;
                ifCommand = "if_acmpne";
            }
            command += ifCommand + " " + elseLabel + "\n";
            command += "iconst_1\n";
            command += "goto " + afterLabel + "\n";
            command += elseLabel + ":\n";
            command += "iconst_0\n";
            command += afterLabel + ":\n";
            command += "invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;\n";
        }
        return command;
    }

    @Override
    public String visit(UnaryExpression unaryExpression) {
        unaryExpression.getOperand().accept(this);
        String operand = unaryExpression.getOperand().accept(this);
        String command = "";
        if (unaryExpression.getOperator().equals(UnaryOperator.not)) { //x xor 1 and 1 = not(x)
            command += operand;
            command += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
            String elseLabel = getFreshLabel();
            String afterLabel = getFreshLabel();
            command += "ldc 1\n";
            command += "ixor\n";
            command += "ldc 1\n";
            command += "iand\n";
            command += "invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;\n";
        }
        if (unaryExpression.getOperator().equals(UnaryOperator.minus)) {
            command += operand;
            command += "invokevirtual java/lang/Integer/intValue()I\n";
            command += "ineg\n";
            command += "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n";
        }
        return command;
    }

    @Override
    public String visit(StructAccess structAccess) {
        //todo

        return null;
    }

    @Override
    public String visit(Identifier identifier) {
        String command = null;
        try {
            String functionKey = FunctionSymbolTableItem.START_KEY + identifier.getName();
            FunctionSymbolTableItem functionSymbolTableItem = (FunctionSymbolTableItem) SymbolTable.root.getItem(functionKey);
            command += "new Fptr\n";
            command += "aload_0";
            command += "aload" + getSlot(identifier.getName());
            command += "invokespecial Fptr/<int>(Ljava/lang/String;)v\n";
        } catch (ItemNotFoundException e) {
            Type idType = identifier.accept(expressionTypeChecker);
            if (idType instanceof IntType || idType instanceof BoolType) {
                command += "iload" + getSlot(identifier.getName());
            } else {
                command += "aload" + getSlot(identifier.getName());
            }
            command += "\n";
        }
        return command;
    }

    @Override
    public String visit(ListAccessByIndex listAccessByIndex) {
        String commands = null;
        Type type = listAccessByIndex.accept(expressionTypeChecker);
        commands += listAccessByIndex.getInstance().accept(this);
        commands += listAccessByIndex.getIndex().accept(this);
        commands += "invokevirtual List/getElement(I)Ljava/lang/Object;\n";
        commands += "checkcast " + makeTypeSignature(type) + "\n";
        if (type instanceof IntType)
            commands += "invokevirtual java/lang/Integer/intValue()I\n";
        if (type instanceof BoolType)
            commands += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
        return commands;
    }

    @Override
    public String visit(FunctionCall functionCall) {
        //todo
        return null;
    }

    @Override
    public String visit(ListSize listSize) {
        String command = listSize.getArg().accept(this);
        command += "invokevirtual List/getSize()I\n";
        command += "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n";
        return command;
    }

    @Override
    public String visit(ListAppend listAppend) {
        String command = null;
        command += listAppend.getListArg().accept(this);
        command += "dup\n";
        command += listAppend.getElementArg().accept(this);
        command += "invokevirtual List/addElement(Ljava/lang/Object;)V\n";
        return command;
    }

    @Override
    public String visit(IntValue intValue) {
        String commands = "";
        commands += "ldc " + intValue.getConstant() + "\n";
        return commands;
    }

    @Override
    public String visit(BoolValue boolValue) {
        String commands = "";
        if (boolValue.getConstant())
            commands += "ldc " + "1\n";
        else
            commands += "ldc " + "0\n";
        return commands;
    }

    @Override
    public String visit(ExprInPar exprInPar) {
        return exprInPar.getInputs().get(0).accept(this);
    }
}
