JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	ADisk.java \
	ADiskUnit.java \
	ActiveTransactionList.java \
	CallbackTracker.java \
	Common.java \
	Disk.java \
	DiskCallback.java \
	DiskResult.java \
	DiskUnit.java \
	DiskWorker.java \
	FlatFS.java \
	LogStatus.java \
	PTree.java\
	PTreeUnit.java\
	SimpleLock.java \
	Transaction.java \
	WriteBackList.java \
	WriteBackThread.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
	$(RM) *.java~


