package main.visitor.codeGenerator;

import main.ast.nodes.*;
import main.ast.nodes.declaration.*;
import main.ast.nodes.declaration.struct.*;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.*;
import main.ast.nodes.expression.values.*;
import main.ast.nodes.expression.values.primitive.*;
import main.ast.nodes.statement.*;
import main.ast.types.*;
import main.ast.types.primitives.*;
import main.symbolTable.*;
import main.symbolTable.exceptions.*;
import main.symbolTable.items.FunctionSymbolTableItem;
import main.symbolTable.items.StructSymbolTableItem;
import main.visitor.Visitor;
import main.visitor.type.ExpressionTypeChecker;
import java.io.*;
import java.util.*;

public class  CodeGenerator extends Visitor<String> {
    ExpressionTypeChecker expressionTypeChecker = new ExpressionTypeChecker();
    private String outputPath;
    private FileWriter currentFile;

    ArrayList<VariableDeclaration> allvars = new ArrayList<>();
    private ArrayList<String> scopeVars = new ArrayList<>();
    boolean structScope = false;

    private FunctionDeclaration curFunc;

    private int numofUsedTmp = 0;
    private int numofUsedLabel = 0;
    private String stackLimit = "128";
    private String localLimit = "128";
    private StructDeclaration currStruct;
    private ArrayList<String> scopeallvars = new ArrayList<>();


    private void copyFile(String toBeCopied, String toBePasted) {
        try {
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
        try{
            File directory = new File(this.outputPath);
            File[] files = directory.listFiles();
            if(files != null)
                for (File file : files)
                    file.delete();
            directory.mkdir();
        }
        catch(SecurityException e) {//unreachable

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
            if(command.startsWith("Label_"))
                this.currentFile.write("\t" + command + "\n");
            else if(command.startsWith("."))
                this.currentFile.write(command + "\n");
            else
                this.currentFile.write("\t\t" + command + "\n");
            this.currentFile.flush();
        } catch (IOException e) {//unreachable

        }
    }

    private void addStaticMainMethod() {
        addCommand(".method public static main([Ljava/lang/String;)V");
        addCommand(".limit stack 128");
        addCommand(".limit locals 128");
        addCommand("new Main");
        addCommand("invokespecial Main/<init>()V");
        addCommand("return");
        addCommand(".end method");
    }

    private int slotOf(String identifier) {
        //todo
        int slotNum = 0;
        if (curFunc != null){
            for (VariableDeclaration arg: curFunc.getArgs()){
                if (arg.getVarName().getName().equals(identifier))
                    return slotNum + 1;
                slotNum += 1;
            }
        }
        for(VariableDeclaration var : allvars)
        {
            if(var.getVarName().getName().equals(identifier))
                return slotNum + 1;
            slotNum += 1;
        }

        if (identifier.equals("")) {
            int temp =numofUsedTmp;
            numofUsedTmp += 1;
            return slotNum + temp + 1;
        }
        return 0;
    }

    private  String castType(Type t){
        if (t instanceof IntType)
            return "java/lang/Integer";
        if (t instanceof BoolType)
            return "java/lang/Boolean";
        if (t instanceof ListType)
            return "List";
        if (t instanceof FptrType)
            return "Fptr";
        if (t instanceof StructType)
            return ((StructType)t).getStructName().getName();
        return null;
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
    private String convertNonPremitive(Type type) {
        String commands = "";
        if(type instanceof IntType)
            commands += "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n";
        if(type instanceof BoolType)
            commands += "invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;\n";
        return commands;
    }

    private String convertPremitive(Type type){
        String commands = "";
        if(type instanceof IntType)
            commands += "invokevirtual java/lang/Integer/intValue()I\n";
        if(type instanceof BoolType)
            commands += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
        return commands;
    }


    private void getStructVars(StructDeclaration structDeclaration){
        String structName = structDeclaration.getStructName().getName();
        for (VariableDeclaration var : allvars) {
            String varName = var.getVarName().getName();
            Type varType = var.getVarType();
            addCommand("aload 0");
            if (varType instanceof FptrType) {
                addCommand("aconst_null");
                addCommand("putfield " + structName + "/" + varName + " L" + castType(varType) + ";\n");
            }
            else if (varType instanceof StructType) {
                String VarStructName = ((StructType)varType).getStructName().getName();
                addCommand("new " + VarStructName);
                addCommand("dup");
                addCommand("invokespecial " + VarStructName + "/<init>()V");
                addCommand("putfield " + structName + "/" + varName + " L" + castType(varType) + ";\n");
            }
            else if (varType instanceof IntType || varType instanceof BoolType) {
                addCommand("ldc 0");
                addCommand(convertNonPremitive(varType));
                addCommand("putfield " + structName + "/" + varName + " L" + castType(varType) + ";\n");
            }
            else {
                addCommand("new List");
                addCommand("dup");
                addCommand("new java/util/ArrayList");
                addCommand("dup");
                addCommand("invokespecial java/util/ArrayList/<init>()V");
                addCommand("invokespecial List/<init>(Ljava/util/ArrayList;)V");
                addCommand("putfield " + structName + "/" + varName + " L" + castType(varType) + ";\n");
            }
        }
    }

    private String getFreshLabel(){
        String label = "Label_";
        label += numofUsedLabel;
        numofUsedLabel++;
        return label;
    }

    @Override
    public String visit(Program program) {
        prepareOutputFolder();

        for(StructDeclaration structDeclaration : program.getStructs()){
            structScope = true;
            structDeclaration.accept(this);
            structScope = false;
            allvars.clear();
        }

        createFile("Main");

        program.getMain().accept(this);
        allvars.clear();
        numofUsedTmp = 0;

        for (FunctionDeclaration functionDeclaration: program.getFunctions()){
            curFunc = functionDeclaration;
            functionDeclaration.accept(this);
            allvars.clear();
            numofUsedTmp = 0;
        }
        return null;
    }

    @Override
    public String visit(StructDeclaration structDeclaration) {
        try{
            currStruct = structDeclaration;
            String structKey = StructSymbolTableItem.START_KEY + structDeclaration.getStructName().getName();
            StructSymbolTableItem structSymbolTableItem = (StructSymbolTableItem) SymbolTable.root.getItem(structKey);
            SymbolTable.push(structSymbolTableItem.getStructSymbolTable());
        }catch (ItemNotFoundException e){//unreachable
        }

        createFile(structDeclaration.getStructName().getName());

        addCommand(".class " + structDeclaration.getStructName().getName());
        addCommand(".super java/lang/Object");

        structDeclaration.getBody().accept(this);
        scopeVars.add(structDeclaration.getStructName().getName());
        addCommand(".method public <init>()V");
        addCommand(".limit stack 128");
        addCommand(".limit locals 128");
        addCommand("aload 0");
        addCommand("invokespecial java/lang/Object/<init>()V");

        for (VariableDeclaration var : allvars) {
            String varName = var.getVarName().getName();
            Type varType = var.getVarType();
            addCommand("aload 0");
            if (varType instanceof FptrType) {
                addCommand("aconst_null");
                addCommand("putfield " + structDeclaration.getStructName().getName() + "/" + varName + " L" + castType(varType) + ";\n");
            }
            else if (varType instanceof StructType) {
                String allvarstructName = ((StructType)varType).getStructName().getName();
                addCommand("new " + allvarstructName);
                addCommand("dup");
                addCommand("invokespecial " + allvarstructName + "/<init>()V");
                addCommand("putfield " + structDeclaration.getStructName().getName() + "/" + varName + " L" + castType(varType) + ";\n");
            }
            else if (varType instanceof IntType || varType instanceof BoolType) {
                addCommand("ldc 0");
                addCommand(convertNonPremitive(varType));
                addCommand("putfield " + structDeclaration.getStructName().getName() + "/" + varName + " L" + castType(varType) + ";\n");
            }
            else {
                addCommand("new List");
                addCommand("dup");
                addCommand("new java/util/ArrayList");
                addCommand("dup");
                addCommand("invokespecial java/util/ArrayList/<init>()V");
                addCommand("invokespecial List/<init>(Ljava/util/ArrayList;)V");
                addCommand("putfield " + structDeclaration.getStructName().getName() + "/" + varName + " L" + castType(varType) + ";\n");
            }
        }
        addCommand("return");
        addCommand(".end method");
        return null;
    }

    @Override
    public String visit(FunctionDeclaration functionDeclaration) {
        ArrayList<Type> argT = new ArrayList<>();
        Type returnType = null;

        try{
            String functionKey = FunctionSymbolTableItem.START_KEY + functionDeclaration.getFunctionName().getName();
//
            FunctionSymbolTableItem functionSymbolTableItem = (FunctionSymbolTableItem) SymbolTable.root.getItem(functionKey);
            SymbolTable.push(functionSymbolTableItem.getFunctionSymbolTable());
            argT = functionSymbolTableItem.getArgTypes();
            returnType = functionSymbolTableItem.getReturnType();
        }
        catch(ItemNotFoundException e){//
        }
        scopeVars.add(functionDeclaration.getFunctionName().getName());
        String header = "";
        header += ".method public " + functionDeclaration.getFunctionName().getName() + "(";
        for (Type vartype: argT)
            header += "L" + castType(vartype) + ";";

        if (returnType instanceof VoidType)
            header += ")V";
        else
            header += ")L" + castType(returnType) + ";";

        addCommand(header);
        addCommand(".limit stack 128");
        addCommand(".limit locals 128");

        functionDeclaration.getBody().accept(this);
        if (returnType instanceof VoidType)
            addCommand("return");
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
        String structName = currStruct.getStructName().getName();

        for(VariableDeclaration var : allvars){
            String varName = var.getVarName().getName();
            Type varType = var.getVarType();
            if(varType instanceof StructType || varType instanceof FptrType){
                addCommand("aload 0");
                addCommand("aconst_null");
                addCommand("putfield " + structName + "/" + varName + " L" + makeTypeSignature(varType) + ";\n");
            }
            else if(varType instanceof IntType || varType instanceof BoolType){
                addCommand("aload 0");
                addCommand("ldc 0");
                if(varType instanceof IntType)
                    addCommand("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
                if(varType instanceof BoolType)
                    addCommand("invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;");
                addCommand("putfield " + structName + "/" + varName + " L" + makeTypeSignature(varType) + ";\n");
            }
        }

        addCommand("return");
        addCommand(".end method");
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
        addCommand(".class Main");
        addCommand(".super java/lang/Object");
        addStaticMainMethod();
        addCommand(".method public <init>()V");
        addCommand(".limit stack 128");
        addCommand(".limit locals 128");
        addCommand("aload 0");
        addCommand("invokespecial java/lang/Object/<init>()V");

        mainDeclaration.getBody().accept(this);
//        addStaticMainMethod();
//        addDefaultConstructor();

        addCommand("return");
        addCommand(".end method");
        return null;
    }

    @Override
    public String visit(VariableDeclaration variableDeclaration) {
        scopeVars.add(variableDeclaration.getVarName().getName());
        allvars.add(variableDeclaration);
        allvars.add(variableDeclaration);
        Type type = variableDeclaration.getVarType();
        String varName = variableDeclaration.getVarName().getName();
        int slot= slotOf(varName);

        if(structScope){
            addCommand(".field " + varName + " L" + castType(type) + ";");
        }
        else {
            if (type instanceof FptrType){
                addCommand("aconst_null");
                addCommand("astore " + slot);
            }
            else if(type instanceof StructType){
                String structName = ((StructType)type).getStructName().getName();
                addCommand("new " + structName);
                addCommand("dup");
                addCommand("invokespecial " + structName  + "/<init>()V");
                addCommand("astore " + slot);
            }
            else if (type instanceof IntType || type instanceof BoolType){
                addCommand("ldc 0");
                addCommand(convertNonPremitive(type));
                addCommand("astore " + slot);
            }
            else{
                addCommand("new List");
                addCommand("dup");
                addCommand("new java/util/ArrayList");
                addCommand("dup");
                addCommand("invokespecial java/util/ArrayList/<init>()V");
                addCommand("invokespecial List/<init>(Ljava/util/ArrayList;)V");
                addCommand("astore " + slot);
            }
        }

        if(variableDeclaration.getDefaultValue() != null){
            addCommand(variableDeclaration.getDefaultValue().accept(this));
            addCommand(convertNonPremitive(type));
            addCommand("astore " + slot);
        }
        return null;
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
        addCommand("pop");
        return null;
    }

    @Override
    public String visit(BlockStmt blockStmt) {
        for (Statement statement: blockStmt.getStatements())
            statement.accept(this);
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
        Expression rexpr = returnStmt.getReturnedExpr();
        if(rexpr == null){
            addCommand("return");
            return null;
        }
        Type type = returnStmt.getReturnedExpr().accept(expressionTypeChecker);

        if (type instanceof VoidType)
            addCommand("return");
        else{
            addCommand(returnStmt.getReturnedExpr().accept(this));
//            addCommand(convertNonPremitive(type));
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
        String labelAfter = getFreshLabel();
        String labelFalse = getFreshLabel();

        if (!loopStmt.getIsDoWhile()){
            addCommand(labelAfter + ":");
            addCommand(loopStmt.getCondition().accept(this));
            addCommand("ifeq " + labelFalse);
            loopStmt.getBody().accept(this);
            addCommand("goto " + labelAfter);
            addCommand(labelFalse + ":");
        }
        else{
            addCommand(labelAfter + ":");
            loopStmt.getBody().accept(this);
            addCommand(loopStmt.getCondition().accept(this));
            addCommand("ifeq " + labelFalse);
            addCommand("goto " + labelAfter);
            addCommand(labelFalse + ":");
        }
        return null;
    }

    @Override
    public String visit(VarDecStmt varDecStmt) {
        for (VariableDeclaration variableDeclaration: varDecStmt.getVars())
            variableDeclaration.accept(this);
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
        addCommand(listSizeStmt.getListSizeExpr().accept(this));
        addCommand("pop");
        return null;
    }

    @Override
    public String visit(BinaryExpression binaryExpression) {
        BinaryOperator operator = binaryExpression.getBinaryOperator();
        Type operandType = binaryExpression.getFirstOperand().accept(expressionTypeChecker);
        String commands = "";

        if (operator == BinaryOperator.add){
            commands += binaryExpression.getFirstOperand().accept(this);
            commands += binaryExpression.getSecondOperand().accept(this);
            commands += "iadd\n";
        }
        else if (operator == BinaryOperator.sub){
            commands += binaryExpression.getFirstOperand().accept(this);
            commands += binaryExpression.getSecondOperand().accept(this);
            commands += "isub\n";
        }
        else if (operator == BinaryOperator.mult){
            commands += binaryExpression.getFirstOperand().accept(this);
            commands += binaryExpression.getSecondOperand().accept(this);
            commands += "imul\n";
        }
        else if (operator == BinaryOperator.div){
            commands += binaryExpression.getFirstOperand().accept(this);
            commands += binaryExpression.getSecondOperand().accept(this);
            commands += "idiv\n";
        }
        else if (operator == BinaryOperator.gt || operator == BinaryOperator.lt){
            commands += binaryExpression.getFirstOperand().accept(this);
            commands += binaryExpression.getSecondOperand().accept(this);

            String labelFalse = getFreshLabel();
            String labelAfter = getFreshLabel();

            if(operator == BinaryOperator.gt)
                commands += "if_icmple " + labelFalse + "\n";
            else
                commands += "if_icmpge " + labelFalse + "\n";
            commands += "ldc " + "1\n";
            commands += "goto " + labelAfter + "\n";
            commands += labelFalse + ":\n";
            commands += "ldc " + "0\n";
            commands += labelAfter + ":\n";
        }
        else if (operator == BinaryOperator.eq){
            commands += binaryExpression.getFirstOperand().accept(this);
            commands += binaryExpression.getSecondOperand().accept(this);

            String labelFalse = getFreshLabel();
            String labelAfter = getFreshLabel();

            if (!(operandType instanceof IntType) && !(operandType instanceof BoolType))
                commands += "if_acmpne " + labelFalse + "\n";
            else
                commands += "if_icmpne " + labelFalse + "\n";
            commands += "ldc " + "1\n";
            commands += "goto " + labelAfter + "\n";
            commands += labelFalse + ":\n";
            commands += "ldc " + "0\n";
            commands += labelAfter + ":\n";
        }
        else if (operator == BinaryOperator.and){
            String labelFalse = getFreshLabel();
            String labelAfter = getFreshLabel();
            commands += binaryExpression.getFirstOperand().accept(this);
            commands += "ifeq " + labelFalse + "\n";
            commands += binaryExpression.getSecondOperand().accept(this);
            commands += "ifeq " + labelFalse + "\n";
            commands += "ldc " + "1\n";
            commands += "goto " + labelAfter + "\n";
            commands += labelFalse + ":\n";
            commands += "ldc " + "0\n";
            commands += labelAfter + ":\n";
        }
        else if(operator == BinaryOperator.or) {
            String labelTrue = getFreshLabel();
            String labelAfter = getFreshLabel();
            commands += binaryExpression.getFirstOperand().accept(this);
            commands += "ifne " + labelTrue + "\n";
            commands += binaryExpression.getSecondOperand().accept(this);
            commands += "ifne " + labelTrue + "\n";
            commands += "ldc " + "0\n";
            commands += "goto " + labelAfter + "\n";
            commands += labelTrue + ":\n";
            commands += "ldc " + "1\n";
            commands += labelAfter + ":\n";
        }
        else if (operator == BinaryOperator.assign){
            Type firstType = binaryExpression.getFirstOperand().accept(expressionTypeChecker);
            Type secondType = binaryExpression.getSecondOperand().accept(expressionTypeChecker);
            String secondOperandCommands = binaryExpression.getSecondOperand().accept(this);

            if(firstType instanceof ListType)
                secondOperandCommands = "new List\ndup\n" + secondOperandCommands + "invokespecial List/<init>(LList;)V\n";
            if(secondType instanceof IntType)
                secondOperandCommands += "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n";
            if(secondType instanceof BoolType)
                secondOperandCommands += "invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;\n";

            if(binaryExpression.getFirstOperand() instanceof Identifier){
                Identifier identifier = (Identifier)binaryExpression.getFirstOperand();
                int slot = slotOf(identifier.getName());
                commands += secondOperandCommands;
                commands += "astore " + slot + "\n";
                commands += "aload " + slot + "\n";
                if (secondType instanceof IntType)
                    commands += "invokevirtual java/lang/Integer/intValue()I\n";
                if (secondType instanceof BoolType)
                    commands += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
            }
            else if (binaryExpression.getFirstOperand() instanceof ListAccessByIndex){
                Expression instance = ((ListAccessByIndex) binaryExpression.getFirstOperand()).getInstance();
                Expression index = ((ListAccessByIndex) binaryExpression.getFirstOperand()).getIndex();
                commands += instance.accept(this);
                commands += index.accept(this);
                commands += secondOperandCommands;
                commands += "invokevirtual List/setElement(ILjava/lang/Object;)V\n";

                commands += instance.accept(this);
                commands += index.accept(this);
                commands += "invokevirtual List/getElement(I)Ljava/lang/Object;\n";
                commands += "checkcast " + castType(secondType) + "\n";
                if (secondType instanceof IntType)
                    commands += "invokevirtual java/lang/Integer/intValue()I\n";
                if (secondType instanceof BoolType)
                    commands += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
            }
            else {
                Expression instance = ((StructAccess) binaryExpression.getFirstOperand()).getInstance();
                StructType instanceType = (StructType)instance.accept(expressionTypeChecker);
                Identifier element = ((StructAccess) binaryExpression.getFirstOperand()).getElement();
                String structName = instanceType.getStructName().getName();
                String varName = element.getName();

                commands += instance.accept(this);
                commands += secondOperandCommands;
                commands += "putfield " + structName + "/" + varName + " L" + castType(firstType) + ";\n";

                commands += instance.accept(this);
                commands += "getfield " + structName + "/" + varName + " L" + castType(firstType) + ";\n";
            }
        }
        return commands;
    }

    @Override
    public String visit(UnaryExpression unaryExpression){
        return null;
    }

    @Override
    public String visit(StructAccess structAccess){
        String commands = null;


        Expression ins = structAccess.getInstance();

        Identifier element = structAccess.getElement();
        StructType insType = (StructType)ins.accept(expressionTypeChecker);
        String structName = insType.getStructName().getName();

        String varName = element.getName();
        Type type = structAccess.accept(expressionTypeChecker);

        commands += ins.accept(this);

        commands += "getfield " + structName + "/" + varName + " L" + castType(type) + ";\n";
        commands += convertPremitive(type);
        return commands;
    }

    @Override
    public String visit(Identifier identifier){
        String command = "";
        String Key = FunctionSymbolTableItem.START_KEY + identifier.getName();
        Type type = identifier.accept(expressionTypeChecker);

        try {
            SymbolTable.root.getItem(Key);
            command += "new Fptr\n";
            command += "dup\n";
            command += "aload 0\n";
            command += "ldc \"" + identifier.getName() + "\"\n";
            command += "invokespecial Fptr/<init>(Ljava/lang/Object;Ljava/lang/String;)V\n";
        }catch (ItemNotFoundException e){

            int slotNum = slotOf(identifier.getName());
            command += "aload " + slotNum + "\n";
            command += convertPremitive(type);
        }
        return command;
    }

    @Override
    public String visit(ListAccessByIndex listAccessByIndex){
        String commands = "";
        String command = "";
        ListType listType = (ListType)listAccessByIndex.getInstance().accept(expressionTypeChecker);
        Type type = listAccessByIndex.accept(expressionTypeChecker);
        commands += listAccessByIndex.getInstance().accept(this);
        commands += listAccessByIndex.getIndex().accept(this);
        commands += "invokevirtual List/getElement(I)Ljava/lang/Object;\n";
        commands += "checkcast " + castType(listType.getType()) + "\n";
        commands += convertPremitive(listType.getType());
        if (type instanceof IntType)
            command += "invokevirtual java/lang/Integer/intValue()I\n";
        if (type instanceof BoolType)
            command += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
        return commands;
    }

    @Override
    public String visit(FunctionCall functionCall){
        String commands = "";
        int tempSlot = slotOf("");

        FptrType fptrType = (FptrType) functionCall.getInstance().accept(expressionTypeChecker);
        Type retType = fptrType.getReturnType();
        String command = "";
        ArrayList<Expression> args = functionCall.getArgs();

        commands += functionCall.getInstance().accept(this);
        commands += "new java/util/ArrayList\n";
        commands += "dup\n";
        commands += "invokespecial java/util/ArrayList/<init>()V\n";
        commands += "astore " + tempSlot + "\n";

        for(Expression arg : args) {
            commands += "aload " + tempSlot + "\n";

            Type argType = arg.accept(expressionTypeChecker);

            if(argType instanceof ListType) {
                commands += "new List\n";
                commands += "dup\n";
            }
            commands += arg.accept(this);

            if(argType instanceof ListType) {
                commands += "invokespecial List/<init>(LList;)V\n";
            }
            commands += convertNonPremitive(argType);
            commands += "invokevirtual java/util/ArrayList/add(Ljava/lang/Object;)Z\n";
            commands += "pop\n";

        }
        commands += "aload " + tempSlot + "\n";
        commands += "invokevirtual Fptr/invoke(Ljava/util/ArrayList;)Ljava/lang/Object;\n";
        if(!(retType instanceof VoidType))
            commands += "checkcast " + makeTypeSignature(retType) + "\n";

        if (retType instanceof IntType)
            command += "invokevirtual java/lang/Integer/intValue()I\n";
        if (retType instanceof BoolType)
            command += "invokevirtual java/lang/Boolean/booleanValue()Z\n";

        if(!(retType instanceof VoidType))
            commands += "checkcast " + castType(retType) + "\n";

        commands += convertPremitive(retType);
        return commands;
    }

    @Override
    public String visit(ListSize listSize){
        String commands = "";
        commands += listSize.getArg().accept(this);
        commands += "invokevirtual List/getSize()I\n";
        return commands;
    }

    @Override
    public String visit(ListAppend listAppend) {
        String commands = "";
        Type elementType = listAppend.getElementArg().accept(expressionTypeChecker);
        commands += listAppend.getListArg().accept(this);
        commands += listAppend.getElementArg().accept(this);
        commands += convertNonPremitive(elementType);
        commands += "invokevirtual List/addElement(Ljava/lang/Object;)V\n";
        return commands;
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
        if(boolValue.getConstant())
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
