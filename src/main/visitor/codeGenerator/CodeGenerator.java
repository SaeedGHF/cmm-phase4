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
import java.lang.reflect.Array;
import java.util.*;

public class CodeGenerator extends Visitor<String> {
    ExpressionTypeChecker expressionTypeChecker;
    private String outputPath;
    private FileWriter currentFile;
    private int labelIndex = 0;
    private boolean inStruct = false;
    private FunctionSymbolTableItem curFunction;

    private StructDeclaration currentStruct;
    private FunctionDeclaration currentFunction;
    private int lastTempValue;
    private String stack_size;
    private String locals_size;
    private int labelNum = 0;
    private ArrayList<String> breaks;
    private ArrayList<String> continues;
    private ArrayList<Integer> temp_vars;

    public CodeGenerator() {
        this.expressionTypeChecker = new ExpressionTypeChecker();
        this.prepareOutputFolder();
        this.lastTempValue = 0;
        this.stack_size = "128";
        this.locals_size = "128";
        this.breaks = new ArrayList<>();
        this.continues = new ArrayList<>();
        this.temp_vars = new ArrayList<>();
    }

    public void setCurFunction(FunctionSymbolTableItem curFunction) {
        this.curFunction = curFunction;
    }

    public FunctionSymbolTableItem getCurFunction() {
        return this.curFunction;
    }

    private int getFresh() {
        return labelIndex++;
    }

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


    private int slotOf(String identifier) {
        int cnt = 1;
        for (VariableDeclaration arg : curFunction.getFunctionDeclaration().getArgs()) {
            if (arg.getVarName().getName().equals(identifier))
                return cnt;
            cnt++;
        }
        return cnt;
    }

    @Override
    public String visit(Program program) {
        //prepareOutputFolder();
        for (StructDeclaration structDeclaration : program.getStructs()) {
            structDeclaration.accept(this);
        }
        createFile("Main");
        program.getMain().accept(this);
        for (FunctionDeclaration functionDeclaration : program.getFunctions()) {
            functionDeclaration.accept(this);
        }
        return null;
    }

    private String makeTypeSignature(Type t, boolean premitive) {
        if (t instanceof IntType) {
            if (premitive)
                return "I";
            else
                return "Ljava/lang/Integer;";
        }
        if (t instanceof BoolType) {
            if (premitive)
                return "Z";
            else
                return "Ljava/lang/Boolean;";
        }
        if (t instanceof StructType)
            return "L" + ((StructType) t).getStructName().getName() + ";";
        if (t instanceof ListType)
            return "LList;";
        if (t instanceof FptrType)
            return "LFptr;";
        if (t instanceof VoidType)
            return "V";
        return null;
    }

    private void initialize(Type t) {
        if (t instanceof IntType) {
            addCommand("ldc 0");
            addCommand("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
        } else if (t instanceof BoolType) {
            addCommand("ldc 0");
            addCommand("invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;");
        } /*else if (t instanceof StringType) {
            addCommand("ldc \"\"");
        } */ else if (t instanceof StructType || t instanceof FptrType) {
            addCommand("aconst_null");
        } else if (t instanceof ListType) {
            addCommand("new List");
            addCommand("dup");
            addCommand("new java/util/ArrayList");
            addCommand("dup");
            addCommand("invokespecial java/util/ArrayList/<init>()V");
            addCommand("dup");
            initialize(((ListType) t).getType());
            //addCommand("checkcast java/lang/Object");// not really sure this should be here
            addCommand("invokevirtual java/util/ArrayList/add(Ljava/lang/Object;)Z");
            addCommand("pop");
            addCommand("invokespecial List/<init>(Ljava/util/ArrayList;)V");
        }
    }

    private String toPremitive(Type t) {
        if (t instanceof IntType)
            return "invokevirtual java/lang/Integer/intValue()I\n";
        if (t instanceof BoolType)
            return "invokevirtual java/lang/Boolean/booleanValue()Z\n";
        return null;
    }

    private String toNonpremitive(Type t) {
        if (t instanceof IntType)
            return "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n";
        if (t instanceof BoolType)
            return "invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;\n";
        return null;
    }

    private int getLabel() {
        return ++this.labelNum;
    }

    @Override
    public String visit(StructDeclaration structDeclaration) {
        createFile(structDeclaration.getStructName().getName());
        inStruct = true;
        String structName = structDeclaration.getStructName().getName();

        addCommand(".class public " + structName + "\n");
        addCommand(".super java/lang/Object\n");

        addCommand(";this is a default constructor");
        addCommand(".method public <init>()V");
        addCommand(".limit locals " + this.stack_size);
        addCommand(".limit stack " + this.locals_size);
        addCommand("aload 0");
        addCommand("invokenonvirtual java/lang/Object/<init>()V");

        /*
        for (FieldDeclaration f : currentClass.getFields()) {
            addCommand("aload_0");
            initialize(f.getVarDeclaration().getType());
            String className = currentClass.getClassName().getName();
            String fieldName = f.getVarDeclaration().getVarName().getName();
            String signiture = makeTypeSignature(f.getVarDeclaration().getType(), false);
            addCommand("putfield " + className + "/" + fieldName + " " + signiture);
        }
         */
        addCommand("return");
        addCommand(".end method");
        addCommand(structDeclaration.getBody().accept(this));
        inStruct = false;
        return null;
    }

    public FunctionSymbolTableItem getFuncSymbolTableItem(String key) {
        try {
            return (FunctionSymbolTableItem) SymbolTable.root.getItem(FunctionSymbolTableItem.START_KEY + key);
        } catch (ItemNotFoundException ignored) {
        }
        return null;
    }

    @Override
    public String visit(FunctionDeclaration functionDeclaration) {
        String funcName = functionDeclaration.getFunctionName().getName();
        Type returnType = functionDeclaration.getReturnType();
        addCommand(".method public " + funcName);
        FunctionSymbolTableItem fsti = getFuncSymbolTableItem(funcName);
        setCurFunction(fsti);
        ArrayList<Type> argTypes = fsti.getArgTypes();
        StringBuilder argList = new StringBuilder("(");
        for (Type t : argTypes) {
            argList.append(makeTypeSignature(t, true));
        }
        argList.append(")");
        argList.append(makeTypeSignature(returnType, true));
        addCommand(argList.toString());
        //addCommand(".limit stack " + this.stack_size);
        //addCommand(".limit locals " + this.locals_size);
        addCommand(functionDeclaration.getBody().accept(this));
        addCommand(".end method");
        return null;
    }

    @Override
    public String visit(MainDeclaration mainDeclaration) {
        addCommand(".class public Main");
        addCommand(".super java/lang/Object");
        addCommand(".method public static main([Ljava/lang/String;)V");
        addCommand(".limit stack " + this.stack_size);
        addCommand(".limit locals " + this.locals_size);
        mainDeclaration.getBody().accept(this);
        addCommand("return");
        addCommand(".end method");
        return null;
    }

    @Override
    public String visit(VariableDeclaration varDeclaration) {        // done
        initialize(varDeclaration.getVarType());
        int slot = slotOf(varDeclaration.getVarName().getName());
        addCommand("astore " + slot);
        return null;
    }


    @Override
    public String visit(SetGetVarDeclaration setGetVarDeclaration) {
        return null;
    }

    @Override
    public String visit(AssignmentStmt assignmentStmt) {
        //todo
        return null;
    }

    @Override
    public String visit(BlockStmt blockStmt) {
        StringBuilder command = new StringBuilder();
        for (Statement stmt : blockStmt.getStatements())
            command.append(stmt.accept(this)).append('\n');
        return command.toString();
    }

    public String dummyInstruction() {
        return """
                iconst_0
                pop
                """;
    }

    @Override
    public String visit(ConditionalStmt conditionalStmt) {
        String elseLabel = "Label" + getFresh();
        String afterLabel = "Label" + getFresh();
        String command = "";
        command += conditionalStmt.getCondition().accept(this);
        command += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
        command += "ifeq " + elseLabel + "\n";
        command += conditionalStmt.getThenBody().accept(this);
        command += "goto " + afterLabel + "\n";
        command += elseLabel + ":\n";
        command += dummyInstruction();
        if (conditionalStmt.getElseBody() != null)
            command += conditionalStmt.getElseBody().accept(this);
        command += afterLabel + ":\n";
        command += dummyInstruction();
        return command;
    }

    @Override
    public String visit(FunctionCallStmt functionCallStmt) {
        String command = "";
        expressionTypeChecker.setInFunctionCallStmt(true);
        command += functionCallStmt.getFunctionCall().accept(this);
        Type t = functionCallStmt.getFunctionCall().accept(expressionTypeChecker);
        if (!(t instanceof VoidType))
            command += "pop\n";
        expressionTypeChecker.setInFunctionCallStmt(false);
        return command;
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
        String command = "";
        command += returnStmt.getReturnedExpr().accept(this);
        Type returnType = returnStmt.getReturnedExpr().accept(expressionTypeChecker);
        if (returnType instanceof VoidType)
            command += "return\n";
        else
            command += "areturn\n";
        return command;
    }

    @Override
    public String visit(LoopStmt loopStmt) {
        //todo
        return null;
    }

    @Override
    public String visit(VarDecStmt varDecStmt) {
        //todo
        return null;
    }

    @Override
    public String visit(ListAppendStmt listAppendStmt) {
        //todo
        return null;
    }

    @Override
    public String visit(ListSizeStmt listSizeStmt) {
        //todo
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
            String elseLabel = "Label" + getFresh();
            String afterLabel = "Label" + getFresh();

            command += commandLeft;
            command += "invokevirtual java/lang/Boolean/booleanValue()Z\n";

            if (operator.equals(BinaryOperator.and))
                command += "ifeq " + elseLabel + "\n";
            else
                command += "ifne " + elseLabel + "\n";

            command += commandRight;
            command += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
            command += "goto " + afterLabel + "\n";
            command += elseLabel + ":\n";

            command += "iconst_" + (operator.equals(BinaryOperator.or) ? "1" : "0") + "\n";

            command += afterLabel + ":\n";
            command += "invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;\n";
        }

        if (operator.equals(BinaryOperator.lt) || operator.equals(BinaryOperator.gt)) {
            String elseLabel = "Label" + getFresh();
            String afterLabel = "Label" + getFresh();
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
            String elseLabel = "Label" + getFresh();
            String afterLabel = "Label" + getFresh();
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

            String elseLabel = "Label" + getFresh();
            String afterLabel = "Label" + getFresh();

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
        FunctionSymbolTableItem fsti = getFuncSymbolTableItem(identifier.getName());
        String command = "";
        if (fsti == null) { //Not a function name
            int slot = slotOf(identifier.getName());
            command = "aload " + slot + "\n";
        } else { //is a function name
            command += "new Fptr\n" +
                    "dup\n" +
                    "aload_1\n" +
                    "ldc \"" + identifier.getName() + "\"\n" +
                    "invokespecial Fptr/<init>(Ljava/lang/Object;Ljava/lang/String;)V\n";
        }
        return command;
    }

    @Override
    public String visit(ListAccessByIndex listAccessByIndex) {
        String commandList = listAccessByIndex.getInstance().accept(this);
        String commandIndex = listAccessByIndex.getIndex().accept(this);

        String command = "";
        command += commandList;
        command += commandIndex;
        command += "invokevirtual java/lang/Integer/intValue()I\n";
        command += "invokevirtual List/getElement(I)Ljava/lang/Object;\n";
        command += "checkcast java/lang/Integer\n";
        return command;
    }

    @Override
    public String visit(FunctionCall functionCall) {
        ArrayList<String> argByteCodes = new ArrayList<>();
        for (Expression expression : functionCall.getArgs()) {
            String bc = expression.accept(this);
            Type type = expression.accept(expressionTypeChecker);
            if (type instanceof ListType) {
                bc = "new List\n" +
                        "dup\n" +
                        bc +
                        "invokespecial List/<init>(LList;)V\n";
            }
            argByteCodes.add(bc);
        }

        Expression instance = functionCall.getInstance();
        FptrType instanceType = (FptrType) instance.accept(expressionTypeChecker);
        String funcName = instanceType.getClass().getName();
        System.out.print(funcName);
        return null;
    }

    @Override
    public String visit(ListSize listSize) {
        String commandList = listSize.accept(this);
        String command = "";
        command += commandList;
        command += "invokevirtual List/getSize()I\n";
        command += "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n";
        return command;
    }

    @Override
    public String visit(ListAppend listAppend) {
        String command = "";
        command += listAppend.getListArg().accept(this);
        command += "dup\n";
        command += listAppend.getElementArg().accept(this);
        command += "invokevirtual List/addElement(Ljava/lang/Object;)V\n";
        return command;
    }

    @Override
    public String visit(IntValue intValue) {
        String command = "";
        command += "ldc " + String.valueOf(intValue.getConstant()) + "\n";
        command += "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n";
        return command;
    }

    @Override
    public String visit(BoolValue boolValue) {
        String command = "";
        if (boolValue.getConstant())
            command += "ldc 1\n";
        else
            command += "ldc 0\n";
        command += "invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;\n";
        return command;
    }

    @Override
    public String visit(ExprInPar exprInPar) {
        return exprInPar.getInputs().get(0).accept(this);
    }
}
