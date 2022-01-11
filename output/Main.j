.class public Main
.super java/lang/Object
.method public static main([Ljava/lang/String;)V
.limit stack 128
.limit locals 128
		new Main
		invokespecial Main/<init>()V
		return
.end method
.method public <init>()V
.limit stack 128
.limit locals 128
		aload_0
		invokespecial java/lang/Object/<init>()V
		iconst_0
		istore_0
		iconst_0
		istore_1
		new List
		astore_2
		new List
		astore_3
		new Order
		astore 4
		
		istore 37
		goto check5
		begin5:
		new ProductCatalog
		astore 5
		
		nullnullaload_2
		dup
		nullaload 5
		invokevirtual List/addElement(Ljava/lang/Object;)V
		
		iinc 37 -1
		check5:
		iload 37
		ifgt begin5
		
		
		
		
		
		istore 50
		goto check8
		begin8:
		
		nullnullaload_3
		dup
		nullaload 4
		invokevirtual List/addElement(Ljava/lang/Object;)V
		
		iinc 50 -1
		check8:
		iload 50
		ifgt begin8
		
		getstatic java/lang/System/out Ljava/io/PrintStream;
		nulliload_1
		invokevirtual java/io/PrintStream/println(I)V
		return
.end method
