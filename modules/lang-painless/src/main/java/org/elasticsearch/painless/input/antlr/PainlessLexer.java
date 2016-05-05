// ANTLR GENERATED CODE: DO NOT EDIT
package org.elasticsearch.painless.input.antlr;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
class PainlessLexer extends Lexer {
  static { RuntimeMetaData.checkVersion("4.5.1", RuntimeMetaData.VERSION); }

  protected static final DFA[] _decisionToDFA;
  protected static final PredictionContextCache _sharedContextCache =
    new PredictionContextCache();
  public static final int
    WS=1, COMMENT=2, LBRACK=3, RBRACK=4, LBRACE=5, RBRACE=6, LP=7, RP=8, DOT=9, 
    COMMA=10, SEMICOLON=11, IF=12, ELSE=13, WHILE=14, DO=15, FOR=16, CONTINUE=17, 
    BREAK=18, RETURN=19, NEW=20, TRY=21, CATCH=22, THROW=23, BOOLNOT=24, BWNOT=25, 
    MUL=26, DIV=27, REM=28, ADD=29, SUB=30, LSH=31, RSH=32, USH=33, LT=34, 
    LTE=35, GT=36, GTE=37, EQ=38, EQR=39, NE=40, NER=41, BWAND=42, XOR=43, 
    BWOR=44, BOOLAND=45, BOOLOR=46, COND=47, COLON=48, INCR=49, DECR=50, ASSIGN=51, 
    AADD=52, ASUB=53, AMUL=54, ADIV=55, AREM=56, AAND=57, AXOR=58, AOR=59, 
    ALSH=60, ARSH=61, AUSH=62, OCTAL=63, HEX=64, INTEGER=65, DECIMAL=66, STRING=67, 
    TRUE=68, FALSE=69, NULL=70, ID=71, EXTINTEGER=72, EXTID=73;
  public static final int EXT = 1;
  public static String[] modeNames = {
    "DEFAULT_MODE", "EXT"
  };

  public static final String[] ruleNames = {
    "WS", "COMMENT", "LBRACK", "RBRACK", "LBRACE", "RBRACE", "LP", "RP", "DOT", 
    "COMMA", "SEMICOLON", "IF", "ELSE", "WHILE", "DO", "FOR", "CONTINUE", 
    "BREAK", "RETURN", "NEW", "TRY", "CATCH", "THROW", "BOOLNOT", "BWNOT", 
    "MUL", "DIV", "REM", "ADD", "SUB", "LSH", "RSH", "USH", "LT", "LTE", "GT", 
    "GTE", "EQ", "EQR", "NE", "NER", "BWAND", "XOR", "BWOR", "BOOLAND", "BOOLOR", 
    "COND", "COLON", "INCR", "DECR", "ASSIGN", "AADD", "ASUB", "AMUL", "ADIV", 
    "AREM", "AAND", "AXOR", "AOR", "ALSH", "ARSH", "AUSH", "OCTAL", "HEX", 
    "INTEGER", "DECIMAL", "STRING", "TRUE", "FALSE", "NULL", "ID", "EXTINTEGER", 
    "EXTID"
  };

  private static final String[] _LITERAL_NAMES = {
    null, null, null, "'{'", "'}'", "'['", "']'", "'('", "')'", "'.'", "','", 
    "';'", "'if'", "'else'", "'while'", "'do'", "'for'", "'continue'", "'break'", 
    "'return'", "'new'", "'try'", "'catch'", "'throw'", "'!'", "'~'", "'*'", 
    "'/'", "'%'", "'+'", "'-'", "'<<'", "'>>'", "'>>>'", "'<'", "'<='", "'>'", 
    "'>='", "'=='", "'==='", "'!='", "'!=='", "'&'", "'^'", "'|'", "'&&'", 
    "'||'", "'?'", "':'", "'++'", "'--'", "'='", "'+='", "'-='", "'*='", "'/='", 
    "'%='", "'&='", "'^='", "'|='", "'<<='", "'>>='", "'>>>='", null, null, 
    null, null, null, "'true'", "'false'", "'null'"
  };
  private static final String[] _SYMBOLIC_NAMES = {
    null, "WS", "COMMENT", "LBRACK", "RBRACK", "LBRACE", "RBRACE", "LP", "RP", 
    "DOT", "COMMA", "SEMICOLON", "IF", "ELSE", "WHILE", "DO", "FOR", "CONTINUE", 
    "BREAK", "RETURN", "NEW", "TRY", "CATCH", "THROW", "BOOLNOT", "BWNOT", 
    "MUL", "DIV", "REM", "ADD", "SUB", "LSH", "RSH", "USH", "LT", "LTE", "GT", 
    "GTE", "EQ", "EQR", "NE", "NER", "BWAND", "XOR", "BWOR", "BOOLAND", "BOOLOR", 
    "COND", "COLON", "INCR", "DECR", "ASSIGN", "AADD", "ASUB", "AMUL", "ADIV", 
    "AREM", "AAND", "AXOR", "AOR", "ALSH", "ARSH", "AUSH", "OCTAL", "HEX", 
    "INTEGER", "DECIMAL", "STRING", "TRUE", "FALSE", "NULL", "ID", "EXTINTEGER", 
    "EXTID"
  };
  public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

  /**
   * @deprecated Use {@link #VOCABULARY} instead.
   */
  @Deprecated
  public static final String[] tokenNames;
  static {
    tokenNames = new String[_SYMBOLIC_NAMES.length];
    for (int i = 0; i < tokenNames.length; i++) {
      tokenNames[i] = VOCABULARY.getLiteralName(i);
      if (tokenNames[i] == null) {
        tokenNames[i] = VOCABULARY.getSymbolicName(i);
      }

      if (tokenNames[i] == null) {
        tokenNames[i] = "<INVALID>";
      }
    }
  }

  @Override
  @Deprecated
  public String[] getTokenNames() {
    return tokenNames;
  }

  @Override

  public Vocabulary getVocabulary() {
    return VOCABULARY;
  }


  public PainlessLexer(CharStream input) {
    super(input);
    _interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
  }

  @Override
  public String getGrammarFileName() { return "PainlessLexer.g4"; }

  @Override
  public String[] getRuleNames() { return ruleNames; }

  @Override
  public String getSerializedATN() { return _serializedATN; }

  @Override
  public String[] getModeNames() { return modeNames; }

  @Override
  public ATN getATN() { return _ATN; }

  public static final String _serializedATN =
    "\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\2K\u01fb\b\1\b\1\4"+
    "\2\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n"+
    "\4\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
    "\t\22\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31"+
    "\t\31\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t"+
    " \4!\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t"+
    "+\4,\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64"+
    "\t\64\4\65\t\65\4\66\t\66\4\67\t\67\48\t8\49\t9\4:\t:\4;\t;\4<\t<\4=\t"+
    "=\4>\t>\4?\t?\4@\t@\4A\tA\4B\tB\4C\tC\4D\tD\4E\tE\4F\tF\4G\tG\4H\tH\4"+
    "I\tI\4J\tJ\3\2\6\2\u0098\n\2\r\2\16\2\u0099\3\2\3\2\3\3\3\3\3\3\3\3\7"+
    "\3\u00a2\n\3\f\3\16\3\u00a5\13\3\3\3\3\3\3\3\3\3\3\3\7\3\u00ac\n\3\f\3"+
    "\16\3\u00af\13\3\3\3\3\3\5\3\u00b3\n\3\3\3\3\3\3\4\3\4\3\5\3\5\3\6\3\6"+
    "\3\7\3\7\3\b\3\b\3\t\3\t\3\n\3\n\3\n\3\n\3\13\3\13\3\f\3\f\3\r\3\r\3\r"+
    "\3\16\3\16\3\16\3\16\3\16\3\17\3\17\3\17\3\17\3\17\3\17\3\20\3\20\3\20"+
    "\3\21\3\21\3\21\3\21\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\23"+
    "\3\23\3\23\3\23\3\23\3\23\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\25\3\25"+
    "\3\25\3\25\3\26\3\26\3\26\3\26\3\27\3\27\3\27\3\27\3\27\3\27\3\30\3\30"+
    "\3\30\3\30\3\30\3\30\3\31\3\31\3\32\3\32\3\33\3\33\3\34\3\34\3\35\3\35"+
    "\3\36\3\36\3\37\3\37\3 \3 \3 \3!\3!\3!\3\"\3\"\3\"\3\"\3#\3#\3$\3$\3$"+
    "\3%\3%\3&\3&\3&\3\'\3\'\3\'\3(\3(\3(\3(\3)\3)\3)\3*\3*\3*\3*\3+\3+\3,"+
    "\3,\3-\3-\3.\3.\3.\3/\3/\3/\3\60\3\60\3\61\3\61\3\62\3\62\3\62\3\63\3"+
    "\63\3\63\3\64\3\64\3\65\3\65\3\65\3\66\3\66\3\66\3\67\3\67\3\67\38\38"+
    "\38\39\39\39\3:\3:\3:\3;\3;\3;\3<\3<\3<\3=\3=\3=\3=\3>\3>\3>\3>\3?\3?"+
    "\3?\3?\3?\3@\3@\6@\u0179\n@\r@\16@\u017a\3@\5@\u017e\n@\3A\3A\3A\6A\u0183"+
    "\nA\rA\16A\u0184\3A\5A\u0188\nA\3B\3B\3B\7B\u018d\nB\fB\16B\u0190\13B"+
    "\5B\u0192\nB\3B\5B\u0195\nB\3C\3C\3C\7C\u019a\nC\fC\16C\u019d\13C\5C\u019f"+
    "\nC\3C\3C\7C\u01a3\nC\fC\16C\u01a6\13C\3C\3C\5C\u01aa\nC\3C\6C\u01ad\n"+
    "C\rC\16C\u01ae\5C\u01b1\nC\3C\5C\u01b4\nC\3D\3D\3D\3D\3D\3D\7D\u01bc\n"+
    "D\fD\16D\u01bf\13D\3D\3D\3D\3D\3D\3D\3D\7D\u01c8\nD\fD\16D\u01cb\13D\3"+
    "D\5D\u01ce\nD\3E\3E\3E\3E\3E\3F\3F\3F\3F\3F\3F\3G\3G\3G\3G\3G\3H\3H\7"+
    "H\u01e2\nH\fH\16H\u01e5\13H\3I\3I\3I\7I\u01ea\nI\fI\16I\u01ed\13I\5I\u01ef"+
    "\nI\3I\3I\3J\3J\7J\u01f5\nJ\fJ\16J\u01f8\13J\3J\3J\6\u00a3\u00ad\u01bd"+
    "\u01c9\2K\4\3\6\4\b\5\n\6\f\7\16\b\20\t\22\n\24\13\26\f\30\r\32\16\34"+
    "\17\36\20 \21\"\22$\23&\24(\25*\26,\27.\30\60\31\62\32\64\33\66\348\35"+
    ":\36<\37> @!B\"D#F$H%J&L\'N(P)R*T+V,X-Z.\\/^\60`\61b\62d\63f\64h\65j\66"+
    "l\67n8p9r:t;v<x=z>|?~@\u0080A\u0082B\u0084C\u0086D\u0088E\u008aF\u008c"+
    "G\u008eH\u0090I\u0092J\u0094K\4\2\3\22\5\2\13\f\17\17\"\"\4\2\f\f\17\17"+
    "\3\2\629\4\2NNnn\4\2ZZzz\5\2\62;CHch\3\2\63;\3\2\62;\b\2FFHHNNffhhnn\4"+
    "\2GGgg\4\2--//\4\2HHhh\4\2$$^^\4\2))^^\5\2C\\aac|\6\2\62;C\\aac|\u0216"+
    "\2\4\3\2\2\2\2\6\3\2\2\2\2\b\3\2\2\2\2\n\3\2\2\2\2\f\3\2\2\2\2\16\3\2"+
    "\2\2\2\20\3\2\2\2\2\22\3\2\2\2\2\24\3\2\2\2\2\26\3\2\2\2\2\30\3\2\2\2"+
    "\2\32\3\2\2\2\2\34\3\2\2\2\2\36\3\2\2\2\2 \3\2\2\2\2\"\3\2\2\2\2$\3\2"+
    "\2\2\2&\3\2\2\2\2(\3\2\2\2\2*\3\2\2\2\2,\3\2\2\2\2.\3\2\2\2\2\60\3\2\2"+
    "\2\2\62\3\2\2\2\2\64\3\2\2\2\2\66\3\2\2\2\28\3\2\2\2\2:\3\2\2\2\2<\3\2"+
    "\2\2\2>\3\2\2\2\2@\3\2\2\2\2B\3\2\2\2\2D\3\2\2\2\2F\3\2\2\2\2H\3\2\2\2"+
    "\2J\3\2\2\2\2L\3\2\2\2\2N\3\2\2\2\2P\3\2\2\2\2R\3\2\2\2\2T\3\2\2\2\2V"+
    "\3\2\2\2\2X\3\2\2\2\2Z\3\2\2\2\2\\\3\2\2\2\2^\3\2\2\2\2`\3\2\2\2\2b\3"+
    "\2\2\2\2d\3\2\2\2\2f\3\2\2\2\2h\3\2\2\2\2j\3\2\2\2\2l\3\2\2\2\2n\3\2\2"+
    "\2\2p\3\2\2\2\2r\3\2\2\2\2t\3\2\2\2\2v\3\2\2\2\2x\3\2\2\2\2z\3\2\2\2\2"+
    "|\3\2\2\2\2~\3\2\2\2\2\u0080\3\2\2\2\2\u0082\3\2\2\2\2\u0084\3\2\2\2\2"+
    "\u0086\3\2\2\2\2\u0088\3\2\2\2\2\u008a\3\2\2\2\2\u008c\3\2\2\2\2\u008e"+
    "\3\2\2\2\2\u0090\3\2\2\2\3\u0092\3\2\2\2\3\u0094\3\2\2\2\4\u0097\3\2\2"+
    "\2\6\u00b2\3\2\2\2\b\u00b6\3\2\2\2\n\u00b8\3\2\2\2\f\u00ba\3\2\2\2\16"+
    "\u00bc\3\2\2\2\20\u00be\3\2\2\2\22\u00c0\3\2\2\2\24\u00c2\3\2\2\2\26\u00c6"+
    "\3\2\2\2\30\u00c8\3\2\2\2\32\u00ca\3\2\2\2\34\u00cd\3\2\2\2\36\u00d2\3"+
    "\2\2\2 \u00d8\3\2\2\2\"\u00db\3\2\2\2$\u00df\3\2\2\2&\u00e8\3\2\2\2(\u00ee"+
    "\3\2\2\2*\u00f5\3\2\2\2,\u00f9\3\2\2\2.\u00fd\3\2\2\2\60\u0103\3\2\2\2"+
    "\62\u0109\3\2\2\2\64\u010b\3\2\2\2\66\u010d\3\2\2\28\u010f\3\2\2\2:\u0111"+
    "\3\2\2\2<\u0113\3\2\2\2>\u0115\3\2\2\2@\u0117\3\2\2\2B\u011a\3\2\2\2D"+
    "\u011d\3\2\2\2F\u0121\3\2\2\2H\u0123\3\2\2\2J\u0126\3\2\2\2L\u0128\3\2"+
    "\2\2N\u012b\3\2\2\2P\u012e\3\2\2\2R\u0132\3\2\2\2T\u0135\3\2\2\2V\u0139"+
    "\3\2\2\2X\u013b\3\2\2\2Z\u013d\3\2\2\2\\\u013f\3\2\2\2^\u0142\3\2\2\2"+
    "`\u0145\3\2\2\2b\u0147\3\2\2\2d\u0149\3\2\2\2f\u014c\3\2\2\2h\u014f\3"+
    "\2\2\2j\u0151\3\2\2\2l\u0154\3\2\2\2n\u0157\3\2\2\2p\u015a\3\2\2\2r\u015d"+
    "\3\2\2\2t\u0160\3\2\2\2v\u0163\3\2\2\2x\u0166\3\2\2\2z\u0169\3\2\2\2|"+
    "\u016d\3\2\2\2~\u0171\3\2\2\2\u0080\u0176\3\2\2\2\u0082\u017f\3\2\2\2"+
    "\u0084\u0191\3\2\2\2\u0086\u019e\3\2\2\2\u0088\u01cd\3\2\2\2\u008a\u01cf"+
    "\3\2\2\2\u008c\u01d4\3\2\2\2\u008e\u01da\3\2\2\2\u0090\u01df\3\2\2\2\u0092"+
    "\u01ee\3\2\2\2\u0094\u01f2\3\2\2\2\u0096\u0098\t\2\2\2\u0097\u0096\3\2"+
    "\2\2\u0098\u0099\3\2\2\2\u0099\u0097\3\2\2\2\u0099\u009a\3\2\2\2\u009a"+
    "\u009b\3\2\2\2\u009b\u009c\b\2\2\2\u009c\5\3\2\2\2\u009d\u009e\7\61\2"+
    "\2\u009e\u009f\7\61\2\2\u009f\u00a3\3\2\2\2\u00a0\u00a2\13\2\2\2\u00a1"+
    "\u00a0\3\2\2\2\u00a2\u00a5\3\2\2\2\u00a3\u00a4\3\2\2\2\u00a3\u00a1\3\2"+
    "\2\2\u00a4\u00a6\3\2\2\2\u00a5\u00a3\3\2\2\2\u00a6\u00b3\t\3\2\2\u00a7"+
    "\u00a8\7\61\2\2\u00a8\u00a9\7,\2\2\u00a9\u00ad\3\2\2\2\u00aa\u00ac\13"+
    "\2\2\2\u00ab\u00aa\3\2\2\2\u00ac\u00af\3\2\2\2\u00ad\u00ae\3\2\2\2\u00ad"+
    "\u00ab\3\2\2\2\u00ae\u00b0\3\2\2\2\u00af\u00ad\3\2\2\2\u00b0\u00b1\7,"+
    "\2\2\u00b1\u00b3\7\61\2\2\u00b2\u009d\3\2\2\2\u00b2\u00a7\3\2\2\2\u00b3"+
    "\u00b4\3\2\2\2\u00b4\u00b5\b\3\2\2\u00b5\7\3\2\2\2\u00b6\u00b7\7}\2\2"+
    "\u00b7\t\3\2\2\2\u00b8\u00b9\7\177\2\2\u00b9\13\3\2\2\2\u00ba\u00bb\7"+
    "]\2\2\u00bb\r\3\2\2\2\u00bc\u00bd\7_\2\2\u00bd\17\3\2\2\2\u00be\u00bf"+
    "\7*\2\2\u00bf\21\3\2\2\2\u00c0\u00c1\7+\2\2\u00c1\23\3\2\2\2\u00c2\u00c3"+
    "\7\60\2\2\u00c3\u00c4\3\2\2\2\u00c4\u00c5\b\n\3\2\u00c5\25\3\2\2\2\u00c6"+
    "\u00c7\7.\2\2\u00c7\27\3\2\2\2\u00c8\u00c9\7=\2\2\u00c9\31\3\2\2\2\u00ca"+
    "\u00cb\7k\2\2\u00cb\u00cc\7h\2\2\u00cc\33\3\2\2\2\u00cd\u00ce\7g\2\2\u00ce"+
    "\u00cf\7n\2\2\u00cf\u00d0\7u\2\2\u00d0\u00d1\7g\2\2\u00d1\35\3\2\2\2\u00d2"+
    "\u00d3\7y\2\2\u00d3\u00d4\7j\2\2\u00d4\u00d5\7k\2\2\u00d5\u00d6\7n\2\2"+
    "\u00d6\u00d7\7g\2\2\u00d7\37\3\2\2\2\u00d8\u00d9\7f\2\2\u00d9\u00da\7"+
    "q\2\2\u00da!\3\2\2\2\u00db\u00dc\7h\2\2\u00dc\u00dd\7q\2\2\u00dd\u00de"+
    "\7t\2\2\u00de#\3\2\2\2\u00df\u00e0\7e\2\2\u00e0\u00e1\7q\2\2\u00e1\u00e2"+
    "\7p\2\2\u00e2\u00e3\7v\2\2\u00e3\u00e4\7k\2\2\u00e4\u00e5\7p\2\2\u00e5"+
    "\u00e6\7w\2\2\u00e6\u00e7\7g\2\2\u00e7%\3\2\2\2\u00e8\u00e9\7d\2\2\u00e9"+
    "\u00ea\7t\2\2\u00ea\u00eb\7g\2\2\u00eb\u00ec\7c\2\2\u00ec\u00ed\7m\2\2"+
    "\u00ed\'\3\2\2\2\u00ee\u00ef\7t\2\2\u00ef\u00f0\7g\2\2\u00f0\u00f1\7v"+
    "\2\2\u00f1\u00f2\7w\2\2\u00f2\u00f3\7t\2\2\u00f3\u00f4\7p\2\2\u00f4)\3"+
    "\2\2\2\u00f5\u00f6\7p\2\2\u00f6\u00f7\7g\2\2\u00f7\u00f8\7y\2\2\u00f8"+
    "+\3\2\2\2\u00f9\u00fa\7v\2\2\u00fa\u00fb\7t\2\2\u00fb\u00fc\7{\2\2\u00fc"+
    "-\3\2\2\2\u00fd\u00fe\7e\2\2\u00fe\u00ff\7c\2\2\u00ff\u0100\7v\2\2\u0100"+
    "\u0101\7e\2\2\u0101\u0102\7j\2\2\u0102/\3\2\2\2\u0103\u0104\7v\2\2\u0104"+
    "\u0105\7j\2\2\u0105\u0106\7t\2\2\u0106\u0107\7q\2\2\u0107\u0108\7y\2\2"+
    "\u0108\61\3\2\2\2\u0109\u010a\7#\2\2\u010a\63\3\2\2\2\u010b\u010c\7\u0080"+
    "\2\2\u010c\65\3\2\2\2\u010d\u010e\7,\2\2\u010e\67\3\2\2\2\u010f\u0110"+
    "\7\61\2\2\u01109\3\2\2\2\u0111\u0112\7\'\2\2\u0112;\3\2\2\2\u0113\u0114"+
    "\7-\2\2\u0114=\3\2\2\2\u0115\u0116\7/\2\2\u0116?\3\2\2\2\u0117\u0118\7"+
    ">\2\2\u0118\u0119\7>\2\2\u0119A\3\2\2\2\u011a\u011b\7@\2\2\u011b\u011c"+
    "\7@\2\2\u011cC\3\2\2\2\u011d\u011e\7@\2\2\u011e\u011f\7@\2\2\u011f\u0120"+
    "\7@\2\2\u0120E\3\2\2\2\u0121\u0122\7>\2\2\u0122G\3\2\2\2\u0123\u0124\7"+
    ">\2\2\u0124\u0125\7?\2\2\u0125I\3\2\2\2\u0126\u0127\7@\2\2\u0127K\3\2"+
    "\2\2\u0128\u0129\7@\2\2\u0129\u012a\7?\2\2\u012aM\3\2\2\2\u012b\u012c"+
    "\7?\2\2\u012c\u012d\7?\2\2\u012dO\3\2\2\2\u012e\u012f\7?\2\2\u012f\u0130"+
    "\7?\2\2\u0130\u0131\7?\2\2\u0131Q\3\2\2\2\u0132\u0133\7#\2\2\u0133\u0134"+
    "\7?\2\2\u0134S\3\2\2\2\u0135\u0136\7#\2\2\u0136\u0137\7?\2\2\u0137\u0138"+
    "\7?\2\2\u0138U\3\2\2\2\u0139\u013a\7(\2\2\u013aW\3\2\2\2\u013b\u013c\7"+
    "`\2\2\u013cY\3\2\2\2\u013d\u013e\7~\2\2\u013e[\3\2\2\2\u013f\u0140\7("+
    "\2\2\u0140\u0141\7(\2\2\u0141]\3\2\2\2\u0142\u0143\7~\2\2\u0143\u0144"+
    "\7~\2\2\u0144_\3\2\2\2\u0145\u0146\7A\2\2\u0146a\3\2\2\2\u0147\u0148\7"+
    "<\2\2\u0148c\3\2\2\2\u0149\u014a\7-\2\2\u014a\u014b\7-\2\2\u014be\3\2"+
    "\2\2\u014c\u014d\7/\2\2\u014d\u014e\7/\2\2\u014eg\3\2\2\2\u014f\u0150"+
    "\7?\2\2\u0150i\3\2\2\2\u0151\u0152\7-\2\2\u0152\u0153\7?\2\2\u0153k\3"+
    "\2\2\2\u0154\u0155\7/\2\2\u0155\u0156\7?\2\2\u0156m\3\2\2\2\u0157\u0158"+
    "\7,\2\2\u0158\u0159\7?\2\2\u0159o\3\2\2\2\u015a\u015b\7\61\2\2\u015b\u015c"+
    "\7?\2\2\u015cq\3\2\2\2\u015d\u015e\7\'\2\2\u015e\u015f\7?\2\2\u015fs\3"+
    "\2\2\2\u0160\u0161\7(\2\2\u0161\u0162\7?\2\2\u0162u\3\2\2\2\u0163\u0164"+
    "\7`\2\2\u0164\u0165\7?\2\2\u0165w\3\2\2\2\u0166\u0167\7~\2\2\u0167\u0168"+
    "\7?\2\2\u0168y\3\2\2\2\u0169\u016a\7>\2\2\u016a\u016b\7>\2\2\u016b\u016c"+
    "\7?\2\2\u016c{\3\2\2\2\u016d\u016e\7@\2\2\u016e\u016f\7@\2\2\u016f\u0170"+
    "\7?\2\2\u0170}\3\2\2\2\u0171\u0172\7@\2\2\u0172\u0173\7@\2\2\u0173\u0174"+
    "\7@\2\2\u0174\u0175\7?\2\2\u0175\177\3\2\2\2\u0176\u0178\7\62\2\2\u0177"+
    "\u0179\t\4\2\2\u0178\u0177\3\2\2\2\u0179\u017a\3\2\2\2\u017a\u0178\3\2"+
    "\2\2\u017a\u017b\3\2\2\2\u017b\u017d\3\2\2\2\u017c\u017e\t\5\2\2\u017d"+
    "\u017c\3\2\2\2\u017d\u017e\3\2\2\2\u017e\u0081\3\2\2\2\u017f\u0180\7\62"+
    "\2\2\u0180\u0182\t\6\2\2\u0181\u0183\t\7\2\2\u0182\u0181\3\2\2\2\u0183"+
    "\u0184\3\2\2\2\u0184\u0182\3\2\2\2\u0184\u0185\3\2\2\2\u0185\u0187\3\2"+
    "\2\2\u0186\u0188\t\5\2\2\u0187\u0186\3\2\2\2\u0187\u0188\3\2\2\2\u0188"+
    "\u0083\3\2\2\2\u0189\u0192\7\62\2\2\u018a\u018e\t\b\2\2\u018b\u018d\t"+
    "\t\2\2\u018c\u018b\3\2\2\2\u018d\u0190\3\2\2\2\u018e\u018c\3\2\2\2\u018e"+
    "\u018f\3\2\2\2\u018f\u0192\3\2\2\2\u0190\u018e\3\2\2\2\u0191\u0189\3\2"+
    "\2\2\u0191\u018a\3\2\2\2\u0192\u0194\3\2\2\2\u0193\u0195\t\n\2\2\u0194"+
    "\u0193\3\2\2\2\u0194\u0195\3\2\2\2\u0195\u0085\3\2\2\2\u0196\u019f\7\62"+
    "\2\2\u0197\u019b\t\b\2\2\u0198\u019a\t\t\2\2\u0199\u0198\3\2\2\2\u019a"+
    "\u019d\3\2\2\2\u019b\u0199\3\2\2\2\u019b\u019c\3\2\2\2\u019c\u019f\3\2"+
    "\2\2\u019d\u019b\3\2\2\2\u019e\u0196\3\2\2\2\u019e\u0197\3\2\2\2\u019f"+
    "\u01a0\3\2\2\2\u01a0\u01a4\5\24\n\2\u01a1\u01a3\t\t\2\2\u01a2\u01a1\3"+
    "\2\2\2\u01a3\u01a6\3\2\2\2\u01a4\u01a2\3\2\2\2\u01a4\u01a5\3\2\2\2\u01a5"+
    "\u01b0\3\2\2\2\u01a6\u01a4\3\2\2\2\u01a7\u01a9\t\13\2\2\u01a8\u01aa\t"+
    "\f\2\2\u01a9\u01a8\3\2\2\2\u01a9\u01aa\3\2\2\2\u01aa\u01ac\3\2\2\2\u01ab"+
    "\u01ad\t\t\2\2\u01ac\u01ab\3\2\2\2\u01ad\u01ae\3\2\2\2\u01ae\u01ac\3\2"+
    "\2\2\u01ae\u01af\3\2\2\2\u01af\u01b1\3\2\2\2\u01b0\u01a7\3\2\2\2\u01b0"+
    "\u01b1\3\2\2\2\u01b1\u01b3\3\2\2\2\u01b2\u01b4\t\r\2\2\u01b3\u01b2\3\2"+
    "\2\2\u01b3\u01b4\3\2\2\2\u01b4\u0087\3\2\2\2\u01b5\u01bd\7$\2\2\u01b6"+
    "\u01b7\7^\2\2\u01b7\u01bc\7$\2\2\u01b8\u01b9\7^\2\2\u01b9\u01bc\7^\2\2"+
    "\u01ba\u01bc\n\16\2\2\u01bb\u01b6\3\2\2\2\u01bb\u01b8\3\2\2\2\u01bb\u01ba"+
    "\3\2\2\2\u01bc\u01bf\3\2\2\2\u01bd\u01be\3\2\2\2\u01bd\u01bb\3\2\2\2\u01be"+
    "\u01c0\3\2\2\2\u01bf\u01bd\3\2\2\2\u01c0\u01ce\7$\2\2\u01c1\u01c9\7)\2"+
    "\2\u01c2\u01c3\7^\2\2\u01c3\u01c8\7)\2\2\u01c4\u01c5\7^\2\2\u01c5\u01c8"+
    "\7^\2\2\u01c6\u01c8\n\17\2\2\u01c7\u01c2\3\2\2\2\u01c7\u01c4\3\2\2\2\u01c7"+
    "\u01c6\3\2\2\2\u01c8\u01cb\3\2\2\2\u01c9\u01ca\3\2\2\2\u01c9\u01c7\3\2"+
    "\2\2\u01ca\u01cc\3\2\2\2\u01cb\u01c9\3\2\2\2\u01cc\u01ce\7)\2\2\u01cd"+
    "\u01b5\3\2\2\2\u01cd\u01c1\3\2\2\2\u01ce\u0089\3\2\2\2\u01cf\u01d0\7v"+
    "\2\2\u01d0\u01d1\7t\2\2\u01d1\u01d2\7w\2\2\u01d2\u01d3\7g\2\2\u01d3\u008b"+
    "\3\2\2\2\u01d4\u01d5\7h\2\2\u01d5\u01d6\7c\2\2\u01d6\u01d7\7n\2\2\u01d7"+
    "\u01d8\7u\2\2\u01d8\u01d9\7g\2\2\u01d9\u008d\3\2\2\2\u01da\u01db\7p\2"+
    "\2\u01db\u01dc\7w\2\2\u01dc\u01dd\7n\2\2\u01dd\u01de\7n\2\2\u01de\u008f"+
    "\3\2\2\2\u01df\u01e3\t\20\2\2\u01e0\u01e2\t\21\2\2\u01e1\u01e0\3\2\2\2"+
    "\u01e2\u01e5\3\2\2\2\u01e3\u01e1\3\2\2\2\u01e3\u01e4\3\2\2\2\u01e4\u0091"+
    "\3\2\2\2\u01e5\u01e3\3\2\2\2\u01e6\u01ef\7\62\2\2\u01e7\u01eb\t\b\2\2"+
    "\u01e8\u01ea\t\t\2\2\u01e9\u01e8\3\2\2\2\u01ea\u01ed\3\2\2\2\u01eb\u01e9"+
    "\3\2\2\2\u01eb\u01ec\3\2\2\2\u01ec\u01ef\3\2\2\2\u01ed\u01eb\3\2\2\2\u01ee"+
    "\u01e6\3\2\2\2\u01ee\u01e7\3\2\2\2\u01ef\u01f0\3\2\2\2\u01f0\u01f1\bI"+
    "\4\2\u01f1\u0093\3\2\2\2\u01f2\u01f6\t\20\2\2\u01f3\u01f5\t\21\2\2\u01f4"+
    "\u01f3\3\2\2\2\u01f5\u01f8\3\2\2\2\u01f6\u01f4\3\2\2\2\u01f6\u01f7\3\2"+
    "\2\2\u01f7\u01f9\3\2\2\2\u01f8\u01f6\3\2\2\2\u01f9\u01fa\bJ\4\2\u01fa"+
    "\u0095\3\2\2\2\37\2\3\u0099\u00a3\u00ad\u00b2\u017a\u017d\u0184\u0187"+
    "\u018e\u0191\u0194\u019b\u019e\u01a4\u01a9\u01ae\u01b0\u01b3\u01bb\u01bd"+
    "\u01c7\u01c9\u01cd\u01e3\u01eb\u01ee\u01f6\5\b\2\2\4\3\2\4\2\2";
  public static final ATN _ATN =
    new ATNDeserializer().deserialize(_serializedATN.toCharArray());
  static {
    _decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
    for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
      _decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
    }
  }
}
