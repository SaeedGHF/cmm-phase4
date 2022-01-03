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
import main.visitor.type.TypeChecker;

import java.io.*;
import java.util.*;
import java.util.function.Function;

public class CodeGeneratorOld extends Visitor<String> {
    ExpressionTypeChecker expressionTypeChecker = new ExpressionTypeChecker();
    private String outputPath;
    private FileWriter currentFile;
    private FunctionDeclaration curFuncDec;
    private int labelIndex = 0;
    private boolean isMain;

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
        //todo
        return 0;
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

    public void addMainDeclaration() {
        String command = """
                .class public Main
                .super java/lang/Object
                """;
        addCommand(command);
    }

    public String addMainInstance() {
        return """
                new Main
                dup
                invokespecial Main/<init>()V
                astore_1
                """;
    }

    public FunctionSymbolTableItem getFuncSymbolTableItem(String key) {
        try {
            return (FunctionSymbolTableItem) SymbolTable.root.getItem("Function_" + key);
        } catch (ItemNotFoundException ignored) {
        }
        return null;
    }

    public StructSymbolTableItem getStructSymbolTableItem(String key) {
        try {
            return (StructSymbolTableItem) SymbolTable.root.getItem("Struct_" + key);
        } catch (ItemNotFoundException ignored) {
        }
        return null;
    }

    @Override
    public String visit(Program program) {
        prepareOutputFolder();

        addMainDeclaration();
        addStaticMainMethod();
        addMainInstance();

        for (StructDeclaration structDeclaration : program.getStructs()) {
            structDeclaration.accept(this);
        }

        createFile("Main");
        String mainDec = program.getMain().accept(this);
        addCommand(mainDec);
        for (FunctionDeclaration functionDeclaration : program.getFunctions()) {
            addCommand(functionDeclaration.accept(this));
        }
        return null;
    }

    @Override
    public String visit(StructDeclaration structDeclaration) {
        String structName = structDeclaration.getStructName().getName();

        createFile(structName);


        String code = ".class public " + structName + "\n";
        code += ".super java/lang/Object\n\n";

        structDeclaration.getBody().accept(this);


        /*
        ArrayList<VarDeclaration> varDeclarations = classDeclaration.getVarDeclarations();
        for (VarDeclaration varDeclaration : varDeclarations) {
            code += ".field protected " + varDeclaration.getIdentifier().getName()
                    + " " + generateCode(varDeclaration.getType()) + "\n";
        }
        code += "\n";
        code += ".method public <init>()V\n" +
                "   .limit stack " + stackSize + "\n" +
                "   .limit locals " + local + "\n" +
                "   aload_0 ; push this\n";
        if (classDeclaration.getParentName() != null) {
            code += "   invokespecial " + classDeclaration.getParentName().getName() + "/<init>()V ; call super\n";
        } else {
            code += "   invokespecial java/lang/Object/<init>()V ; call super\n";
        }


        for (VarDeclaration varDeclaration : varDeclarations) {
            if (varDeclaration.getType() instanceof IntType || varDeclaration.getType() instanceof BooleanType || varDeclaration.getType() instanceof StringType) {
                code += "\n   ;Initializing " + varDeclaration.getIdentifier().getName() +
                        "\n   aload_0\n";
                code += varDeclaration.getType() instanceof StringType ? "   ldc \"\"\n" : "   iconst_0\n";
                code += "   putfield ";
                code += classDeclaration.getName().getName() +
                        "/" + varDeclaration.getIdentifier().getName() +
                        " " + generateCode(varDeclaration.getType()) + "\n";
            }
        }
        code += "   return\n" +
                ".end method";

        ArrayList<MethodDeclaration> methodDeclarations = classDeclaration.getMethodDeclarations();
        for (MethodDeclaration methodDeclaration : methodDeclarations) {
            code += "\n\n" + methodDeclaration.getCode() + "\n\n";
        }

        classDeclaration.setCode(code);
         */
        return code;

    }

    private String generateCode(Type type) {
        String code = "";
        if (type instanceof ListType)
            code = "List";
        else if (type instanceof IntType)
            code = "java/lang/Integer;";
        else if (type instanceof BoolType)
            code = "java/lang/Boolean;";
        else if (type instanceof FptrType)
            code = "Fptr";
        else if (type instanceof StructType)
            code = ((StructType) type).getStructName().getName();
        return code;
    }

    private String getFunctionDeclarationArgsString(FunctionDeclaration functionDeclaration) {
        String functionName = functionDeclaration.getFunctionName().getName();
        String types = "";
        FunctionSymbolTableItem functionItem;
        try {
            functionItem = (FunctionSymbolTableItem) SymbolTable.root.getItem(FunctionSymbolTableItem.START_KEY + functionName);
        } catch (ItemNotFoundException e) {
            return "";
        }
        ArrayList<Type> functionItemArgsType = functionItem.getArgTypes();
        for (Type aFunctionItemArgType : functionItemArgsType) {
            types += generateCode(aFunctionItemArgType);
        }
        return types;
    }

    @Override
    public String visit(FunctionDeclaration functionDeclaration) {
        String returnTypeCode = generateCode(functionDeclaration.getReturnType());
        String functionName = functionDeclaration.getFunctionName().getName();
        String args = getFunctionDeclarationArgsString(functionDeclaration);
        String staticy = "";
        String returnCode = "";
        String code = ".class public " + functionName + "(" + args + ")";
        return code;
        /*
        switch (returnTypeCode) {
            case "I":
                returnCode = "   ireturn";
                break;
            case "Z":
                returnCode = "   ireturn";
                break;
            case "Ljava/lang/String;":
                returnCode = "  areturn";
                break;
            case "[I":
                returnCode = "  areturn";
                break;
            case "V":
                returnCode = "   return";
                break;
            case "L":
                returnCode = "   areturn";
                break;
            default:
                returnCode = "   return";
                break;
        }

        if (CodeGenerationVisitor.inMain) {
            returnTypeCode = "V";
            args = "[Ljava/lang/String;";
            staticy = "static ";
            returnCode = "\n   return";
        }
        String code = ".method public " + staticy + methodName + "(" + args + ")" + returnTypeCode + "\n";
        code += "   .limit stack " + stackSize + "\n" +
                "   .limit locals " + local + "\n";

        ArrayList<VarDeclaration> varDeclarations = methodDeclaration.getLocalVars();
        for (VarDeclaration varDeclaration : varDeclarations) {
            if (!varDeclaration.getCode().equals("")) {
                code += "\n   ;Initializing " + varDeclaration.getIdentifier().getName();
                code += "\n" + varDeclaration.getCode();
            }
        }

        ArrayList<Statement> statements = methodDeclaration.getBody();
        for (Statement statement : statements) {
            code += "\n   ; " + statement.toString() + "\n" +
                    statement.getCode();
        }

        code += CodeGenerationVisitor.inMain ? "" : "\n" + methodDeclaration.getReturnValue().getCode() + "\n";

        code += returnCode + "\n";
        code += ".end method\n";

        methodDeclaration.setCode(code);
        return code;
         */
    }

    @Override
    public String visit(MainDeclaration mainDeclaration) {
        //todo
        return null;
    }

    @Override
    public String visit(VariableDeclaration variableDeclaration) {
        //todo
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
        //todo
        return null;
    }

    @Override
    public String visit(ConditionalStmt conditionalStmt) {
        //todo
        return null;
    }

    @Override
    public String visit(FunctionCallStmt functionCallStmt) {
        //todo
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
        //todo
        return null;
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

    @Override
    public String visit(BinaryExpression binaryExpression) {
        //todo
        return null;
    }

    @Override
    public String visit(UnaryExpression unaryExpression) {
        return null;
    }

    @Override
    public String visit(StructAccess structAccess) {
        //todo
        return null;
    }

    @Override
    public String visit(Identifier identifier) {
        //todo
        return null;
    }

    @Override
    public String visit(ListAccessByIndex listAccessByIndex) {
        //todo
        return null;
    }

    @Override
    public String visit(FunctionCall functionCall) {
        //todo
        return null;
    }

    @Override
    public String visit(ListSize listSize) {
        //todo
        return null;
    }

    @Override
    public String visit(ListAppend listAppend) {
        //todo
        return null;
    }

    @Override
    public String visit(IntValue intValue) {
        //todo
        return null;
    }

    @Override
    public String visit(BoolValue boolValue) {
        //todo
        return null;
    }

    @Override
    public String visit(ExprInPar exprInPar) {
        return exprInPar.getInputs().get(0).accept(this);
    }
}
