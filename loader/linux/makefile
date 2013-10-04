LIBS = -lm
OBJS = safebox.o
CFLAGS = -Wall
OUTPUT = safebox
DEST = ~/safebox

$(OUTPUT): ${OBJS}
	gcc -o $@ ${OBJS} ${LIBS}
install:
	mkdir $(DEST)
	cp -f $(OUTPUT) $(DEST)
	chmod +x $(DEST)/$(OUTPUT)
clean:
	rm -f $(OUTPUT) ${OBJS}
 
