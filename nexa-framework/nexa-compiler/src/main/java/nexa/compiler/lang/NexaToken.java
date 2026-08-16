package nexa.compiler.lang;

public record NexaToken(Kind kind, String text, SourceSpan span) {
    public enum Kind {
        IDENT, INT, FLOAT, STRING, TRUE, FALSE,
        LET, CONST, TYPE, RETURN, FOR, IN,
        BOOLEAN, INT8, INT16, INT32, INT64, UINT8, UINT16, UINT32, UINT64,
        FLOAT32, FLOAT64, STRING_TYPE, ARRAY, OBJECT,
        LBRACE,RBRACE,LBRACKET,RBRACKET,LPAREN,RPAREN, COLON,COMMA,SEMICOLON,DOT,
        PLUS,MINUS,STAR,SLASH,BANG,EQ,EQEQ,NE,LT,LE,GT,GE,AND,OR,
        EOF
    }
}
