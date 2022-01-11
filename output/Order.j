.class public Order
.super java/lang/Object
.method public <init>()V
.limit stack 128
.limit locals 128
		aload_0
		invokespecial java/lang/Object/<init>()V
		iconst_0
		istore_1
		new ProductCatalog
		astore_2
		return
.end method
.method public createOrder(ProductCatalog;java/lang/Integer;)Order
		new Order
		astore_3
		
		
		nullaload_3
		areturn
.end method
.method public getSum(List;)java/lang/Integer
		ldc 0
		iconst_0
		istore_2
		ldc 0
		iconst_0
		istore_3
		iconst_0
		istore 4
		istore 22
		goto check2
		begin2:
		
		
		
		iinc 22 -1
		check2:
		iload 22
		ifgt begin2
		nulliload_3
		invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;
		areturn
.end method
