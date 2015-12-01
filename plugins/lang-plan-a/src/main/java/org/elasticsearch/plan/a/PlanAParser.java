// ANTLR GENERATED CODE: DO NOT EDIT
package org.elasticsearch.plan.a;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
class PlanAParser extends Parser {
  static { RuntimeMetaData.checkVersion("4.5.1", RuntimeMetaData.VERSION); }

  protected static final DFA[] _decisionToDFA;
  protected static final PredictionContextCache _sharedContextCache =
    new PredictionContextCache();
  public static final int
    WS=1, COMMENT=2, LBRACK=3, RBRACK=4, LBRACE=5, RBRACE=6, LP=7, RP=8, DOT=9, 
    COMMA=10, SEMICOLON=11, IF=12, ELSE=13, WHILE=14, DO=15, FOR=16, CONTINUE=17, 
    BREAK=18, RETURN=19, NEW=20, TRY=21, CATCH=22, THROW=23, BOOLNOT=24, BWNOT=25, 
    MUL=26, DIV=27, REM=28, ADD=29, SUB=30, LSH=31, RSH=32, USH=33, LT=34, 
    LTE=35, GT=36, GTE=37, EQ=38, EQR=39, NE=40, NER=41, BWAND=42, BWXOR=43, 
    BWOR=44, BOOLAND=45, BOOLOR=46, COND=47, COLON=48, INCR=49, DECR=50, ASSIGN=51, 
    AADD=52, ASUB=53, AMUL=54, ADIV=55, AREM=56, AAND=57, AXOR=58, AOR=59, 
    ALSH=60, ARSH=61, AUSH=62, ACAT=63, OCTAL=64, HEX=65, INTEGER=66, DECIMAL=67, 
    STRING=68, CHAR=69, TRUE=70, FALSE=71, NULL=72, TYPE=73, ID=74, EXTINTEGER=75, 
    EXTID=76;
  public static final int
    RULE_source = 0, RULE_statement = 1, RULE_block = 2, RULE_empty = 3, RULE_initializer = 4, 
    RULE_afterthought = 5, RULE_declaration = 6, RULE_decltype = 7, RULE_declvar = 8, 
    RULE_expression = 9, RULE_extstart = 10, RULE_extprec = 11, RULE_extcast = 12, 
    RULE_extbrace = 13, RULE_extdot = 14, RULE_exttype = 15, RULE_extcall = 16, 
    RULE_extvar = 17, RULE_extfield = 18, RULE_extnew = 19, RULE_extstring = 20, 
    RULE_arguments = 21, RULE_increment = 22;
  public static final String[] ruleNames = {
    "source", "statement", "block", "empty", "initializer", "afterthought", 
    "declaration", "decltype", "declvar", "expression", "extstart", "extprec", 
    "extcast", "extbrace", "extdot", "exttype", "extcall", "extvar", "extfield", 
    "extnew", "extstring", "arguments", "increment"
  };

  private static final String[] _LITERAL_NAMES = {
    null, null, null, "'{'", "'}'", "'['", "']'", "'('", "')'", "'.'", "','", 
    "';'", "'if'", "'else'", "'while'", "'do'", "'for'", "'continue'", "'break'", 
    "'return'", "'new'", "'try'", "'catch'", "'throw'", "'!'", "'~'", "'*'", 
    "'/'", "'%'", "'+'", "'-'", "'<<'", "'>>'", "'>>>'", "'<'", "'<='", "'>'", 
    "'>='", "'=='", "'==='", "'!='", "'!=='", "'&'", "'^'", "'|'", "'&&'", 
    "'||'", "'?'", "':'", "'++'", "'--'", "'='", "'+='", "'-='", "'*='", "'/='", 
    "'%='", "'&='", "'^='", "'|='", "'<<='", "'>>='", "'>>>='", "'..='", null, 
    null, null, null, null, null, "'true'", "'false'", "'null'"
  };
  private static final String[] _SYMBOLIC_NAMES = {
    null, "WS", "COMMENT", "LBRACK", "RBRACK", "LBRACE", "RBRACE", "LP", "RP", 
    "DOT", "COMMA", "SEMICOLON", "IF", "ELSE", "WHILE", "DO", "FOR", "CONTINUE", 
    "BREAK", "RETURN", "NEW", "TRY", "CATCH", "THROW", "BOOLNOT", "BWNOT", 
    "MUL", "DIV", "REM", "ADD", "SUB", "LSH", "RSH", "USH", "LT", "LTE", "GT", 
    "GTE", "EQ", "EQR", "NE", "NER", "BWAND", "BWXOR", "BWOR", "BOOLAND", 
    "BOOLOR", "COND", "COLON", "INCR", "DECR", "ASSIGN", "AADD", "ASUB", "AMUL", 
    "ADIV", "AREM", "AAND", "AXOR", "AOR", "ALSH", "ARSH", "AUSH", "ACAT", 
    "OCTAL", "HEX", "INTEGER", "DECIMAL", "STRING", "CHAR", "TRUE", "FALSE", 
    "NULL", "TYPE", "ID", "EXTINTEGER", "EXTID"
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

  @Override
  public String getGrammarFileName() { return "PlanAParser.g4"; }

  @Override
  public String[] getRuleNames() { return ruleNames; }

  @Override
  public String getSerializedATN() { return _serializedATN; }

  @Override
  public ATN getATN() { return _ATN; }

  public PlanAParser(TokenStream input) {
    super(input);
    _interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
  }
  public static class SourceContext extends ParserRuleContext {
    public TerminalNode EOF() { return getToken(PlanAParser.EOF, 0); }
    public List<StatementContext> statement() {
      return getRuleContexts(StatementContext.class);
    }
    public StatementContext statement(int i) {
      return getRuleContext(StatementContext.class,i);
    }
    public SourceContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_source; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitSource(this);
      else return visitor.visitChildren(this);
    }
  }

  public final SourceContext source() throws RecognitionException {
    SourceContext _localctx = new SourceContext(_ctx, getState());
    enterRule(_localctx, 0, RULE_source);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(47); 
      _errHandler.sync(this);
      _la = _input.LA(1);
      do {
        {
        {
        setState(46);
        statement();
        }
        }
        setState(49); 
        _errHandler.sync(this);
        _la = _input.LA(1);
      } while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << LP) | (1L << IF) | (1L << WHILE) | (1L << DO) | (1L << FOR) | (1L << CONTINUE) | (1L << BREAK) | (1L << RETURN) | (1L << NEW) | (1L << TRY) | (1L << THROW) | (1L << BOOLNOT) | (1L << BWNOT) | (1L << ADD) | (1L << SUB) | (1L << INCR) | (1L << DECR))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (OCTAL - 64)) | (1L << (HEX - 64)) | (1L << (INTEGER - 64)) | (1L << (DECIMAL - 64)) | (1L << (STRING - 64)) | (1L << (CHAR - 64)) | (1L << (TRUE - 64)) | (1L << (FALSE - 64)) | (1L << (NULL - 64)) | (1L << (TYPE - 64)) | (1L << (ID - 64)))) != 0) );
      setState(51);
      match(EOF);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class StatementContext extends ParserRuleContext {
    public StatementContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_statement; }
   
    public StatementContext() { }
    public void copyFrom(StatementContext ctx) {
      super.copyFrom(ctx);
    }
  }
  public static class DeclContext extends StatementContext {
    public DeclarationContext declaration() {
      return getRuleContext(DeclarationContext.class,0);
    }
    public TerminalNode SEMICOLON() { return getToken(PlanAParser.SEMICOLON, 0); }
    public DeclContext(StatementContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitDecl(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class BreakContext extends StatementContext {
    public TerminalNode BREAK() { return getToken(PlanAParser.BREAK, 0); }
    public TerminalNode SEMICOLON() { return getToken(PlanAParser.SEMICOLON, 0); }
    public BreakContext(StatementContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitBreak(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class ThrowContext extends StatementContext {
    public TerminalNode THROW() { return getToken(PlanAParser.THROW, 0); }
    public ExtstartContext extstart() {
      return getRuleContext(ExtstartContext.class,0);
    }
    public TerminalNode SEMICOLON() { return getToken(PlanAParser.SEMICOLON, 0); }
    public ThrowContext(StatementContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitThrow(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class ContinueContext extends StatementContext {
    public TerminalNode CONTINUE() { return getToken(PlanAParser.CONTINUE, 0); }
    public TerminalNode SEMICOLON() { return getToken(PlanAParser.SEMICOLON, 0); }
    public ContinueContext(StatementContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitContinue(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class ForContext extends StatementContext {
    public TerminalNode FOR() { return getToken(PlanAParser.FOR, 0); }
    public TerminalNode LP() { return getToken(PlanAParser.LP, 0); }
    public List<TerminalNode> SEMICOLON() { return getTokens(PlanAParser.SEMICOLON); }
    public TerminalNode SEMICOLON(int i) {
      return getToken(PlanAParser.SEMICOLON, i);
    }
    public TerminalNode RP() { return getToken(PlanAParser.RP, 0); }
    public BlockContext block() {
      return getRuleContext(BlockContext.class,0);
    }
    public EmptyContext empty() {
      return getRuleContext(EmptyContext.class,0);
    }
    public InitializerContext initializer() {
      return getRuleContext(InitializerContext.class,0);
    }
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public AfterthoughtContext afterthought() {
      return getRuleContext(AfterthoughtContext.class,0);
    }
    public ForContext(StatementContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitFor(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class TryContext extends StatementContext {
    public TerminalNode TRY() { return getToken(PlanAParser.TRY, 0); }
    public List<BlockContext> block() {
      return getRuleContexts(BlockContext.class);
    }
    public BlockContext block(int i) {
      return getRuleContext(BlockContext.class,i);
    }
    public TerminalNode CATCH() { return getToken(PlanAParser.CATCH, 0); }
    public TerminalNode LP() { return getToken(PlanAParser.LP, 0); }
    public TerminalNode RP() { return getToken(PlanAParser.RP, 0); }
    public TerminalNode TYPE() { return getToken(PlanAParser.TYPE, 0); }
    public TerminalNode ID() { return getToken(PlanAParser.ID, 0); }
    public TryContext(StatementContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitTry(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class ExprContext extends StatementContext {
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public TerminalNode SEMICOLON() { return getToken(PlanAParser.SEMICOLON, 0); }
    public ExprContext(StatementContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitExpr(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class DoContext extends StatementContext {
    public TerminalNode DO() { return getToken(PlanAParser.DO, 0); }
    public BlockContext block() {
      return getRuleContext(BlockContext.class,0);
    }
    public TerminalNode WHILE() { return getToken(PlanAParser.WHILE, 0); }
    public TerminalNode LP() { return getToken(PlanAParser.LP, 0); }
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public TerminalNode RP() { return getToken(PlanAParser.RP, 0); }
    public TerminalNode SEMICOLON() { return getToken(PlanAParser.SEMICOLON, 0); }
    public DoContext(StatementContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitDo(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class WhileContext extends StatementContext {
    public TerminalNode WHILE() { return getToken(PlanAParser.WHILE, 0); }
    public TerminalNode LP() { return getToken(PlanAParser.LP, 0); }
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public TerminalNode RP() { return getToken(PlanAParser.RP, 0); }
    public BlockContext block() {
      return getRuleContext(BlockContext.class,0);
    }
    public EmptyContext empty() {
      return getRuleContext(EmptyContext.class,0);
    }
    public WhileContext(StatementContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitWhile(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class IfContext extends StatementContext {
    public TerminalNode IF() { return getToken(PlanAParser.IF, 0); }
    public TerminalNode LP() { return getToken(PlanAParser.LP, 0); }
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public TerminalNode RP() { return getToken(PlanAParser.RP, 0); }
    public List<BlockContext> block() {
      return getRuleContexts(BlockContext.class);
    }
    public BlockContext block(int i) {
      return getRuleContext(BlockContext.class,i);
    }
    public TerminalNode ELSE() { return getToken(PlanAParser.ELSE, 0); }
    public IfContext(StatementContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitIf(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class ReturnContext extends StatementContext {
    public TerminalNode RETURN() { return getToken(PlanAParser.RETURN, 0); }
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public TerminalNode SEMICOLON() { return getToken(PlanAParser.SEMICOLON, 0); }
    public ReturnContext(StatementContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitReturn(this);
      else return visitor.visitChildren(this);
    }
  }

  public final StatementContext statement() throws RecognitionException {
    StatementContext _localctx = new StatementContext(_ctx, getState());
    enterRule(_localctx, 2, RULE_statement);
    int _la;
    try {
      setState(133);
      switch ( getInterpreter().adaptivePredict(_input,14,_ctx) ) {
      case 1:
        _localctx = new IfContext(_localctx);
        enterOuterAlt(_localctx, 1);
        {
        setState(53);
        match(IF);
        setState(54);
        match(LP);
        setState(55);
        expression(0);
        setState(56);
        match(RP);
        setState(57);
        block();
        setState(60);
        switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
        case 1:
          {
          setState(58);
          match(ELSE);
          setState(59);
          block();
          }
          break;
        }
        }
        break;
      case 2:
        _localctx = new WhileContext(_localctx);
        enterOuterAlt(_localctx, 2);
        {
        setState(62);
        match(WHILE);
        setState(63);
        match(LP);
        setState(64);
        expression(0);
        setState(65);
        match(RP);
        setState(68);
        switch (_input.LA(1)) {
        case LBRACK:
        case LP:
        case IF:
        case WHILE:
        case DO:
        case FOR:
        case CONTINUE:
        case BREAK:
        case RETURN:
        case NEW:
        case TRY:
        case THROW:
        case BOOLNOT:
        case BWNOT:
        case ADD:
        case SUB:
        case INCR:
        case DECR:
        case OCTAL:
        case HEX:
        case INTEGER:
        case DECIMAL:
        case STRING:
        case CHAR:
        case TRUE:
        case FALSE:
        case NULL:
        case TYPE:
        case ID:
          {
          setState(66);
          block();
          }
          break;
        case SEMICOLON:
          {
          setState(67);
          empty();
          }
          break;
        default:
          throw new NoViableAltException(this);
        }
        }
        break;
      case 3:
        _localctx = new DoContext(_localctx);
        enterOuterAlt(_localctx, 3);
        {
        setState(70);
        match(DO);
        setState(71);
        block();
        setState(72);
        match(WHILE);
        setState(73);
        match(LP);
        setState(74);
        expression(0);
        setState(75);
        match(RP);
        setState(77);
        _la = _input.LA(1);
        if (_la==SEMICOLON) {
          {
          setState(76);
          match(SEMICOLON);
          }
        }

        }
        break;
      case 4:
        _localctx = new ForContext(_localctx);
        enterOuterAlt(_localctx, 4);
        {
        setState(79);
        match(FOR);
        setState(80);
        match(LP);
        setState(82);
        _la = _input.LA(1);
        if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << LP) | (1L << NEW) | (1L << BOOLNOT) | (1L << BWNOT) | (1L << ADD) | (1L << SUB) | (1L << INCR) | (1L << DECR))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (OCTAL - 64)) | (1L << (HEX - 64)) | (1L << (INTEGER - 64)) | (1L << (DECIMAL - 64)) | (1L << (STRING - 64)) | (1L << (CHAR - 64)) | (1L << (TRUE - 64)) | (1L << (FALSE - 64)) | (1L << (NULL - 64)) | (1L << (TYPE - 64)) | (1L << (ID - 64)))) != 0)) {
          {
          setState(81);
          initializer();
          }
        }

        setState(84);
        match(SEMICOLON);
        setState(86);
        _la = _input.LA(1);
        if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << LP) | (1L << NEW) | (1L << BOOLNOT) | (1L << BWNOT) | (1L << ADD) | (1L << SUB) | (1L << INCR) | (1L << DECR))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (OCTAL - 64)) | (1L << (HEX - 64)) | (1L << (INTEGER - 64)) | (1L << (DECIMAL - 64)) | (1L << (STRING - 64)) | (1L << (CHAR - 64)) | (1L << (TRUE - 64)) | (1L << (FALSE - 64)) | (1L << (NULL - 64)) | (1L << (TYPE - 64)) | (1L << (ID - 64)))) != 0)) {
          {
          setState(85);
          expression(0);
          }
        }

        setState(88);
        match(SEMICOLON);
        setState(90);
        _la = _input.LA(1);
        if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << LP) | (1L << NEW) | (1L << BOOLNOT) | (1L << BWNOT) | (1L << ADD) | (1L << SUB) | (1L << INCR) | (1L << DECR))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (OCTAL - 64)) | (1L << (HEX - 64)) | (1L << (INTEGER - 64)) | (1L << (DECIMAL - 64)) | (1L << (STRING - 64)) | (1L << (CHAR - 64)) | (1L << (TRUE - 64)) | (1L << (FALSE - 64)) | (1L << (NULL - 64)) | (1L << (TYPE - 64)) | (1L << (ID - 64)))) != 0)) {
          {
          setState(89);
          afterthought();
          }
        }

        setState(92);
        match(RP);
        setState(95);
        switch (_input.LA(1)) {
        case LBRACK:
        case LP:
        case IF:
        case WHILE:
        case DO:
        case FOR:
        case CONTINUE:
        case BREAK:
        case RETURN:
        case NEW:
        case TRY:
        case THROW:
        case BOOLNOT:
        case BWNOT:
        case ADD:
        case SUB:
        case INCR:
        case DECR:
        case OCTAL:
        case HEX:
        case INTEGER:
        case DECIMAL:
        case STRING:
        case CHAR:
        case TRUE:
        case FALSE:
        case NULL:
        case TYPE:
        case ID:
          {
          setState(93);
          block();
          }
          break;
        case SEMICOLON:
          {
          setState(94);
          empty();
          }
          break;
        default:
          throw new NoViableAltException(this);
        }
        }
        break;
      case 5:
        _localctx = new DeclContext(_localctx);
        enterOuterAlt(_localctx, 5);
        {
        setState(97);
        declaration();
        setState(99);
        _la = _input.LA(1);
        if (_la==SEMICOLON) {
          {
          setState(98);
          match(SEMICOLON);
          }
        }

        }
        break;
      case 6:
        _localctx = new ContinueContext(_localctx);
        enterOuterAlt(_localctx, 6);
        {
        setState(101);
        match(CONTINUE);
        setState(103);
        _la = _input.LA(1);
        if (_la==SEMICOLON) {
          {
          setState(102);
          match(SEMICOLON);
          }
        }

        }
        break;
      case 7:
        _localctx = new BreakContext(_localctx);
        enterOuterAlt(_localctx, 7);
        {
        setState(105);
        match(BREAK);
        setState(107);
        _la = _input.LA(1);
        if (_la==SEMICOLON) {
          {
          setState(106);
          match(SEMICOLON);
          }
        }

        }
        break;
      case 8:
        _localctx = new ReturnContext(_localctx);
        enterOuterAlt(_localctx, 8);
        {
        setState(109);
        match(RETURN);
        setState(110);
        expression(0);
        setState(112);
        _la = _input.LA(1);
        if (_la==SEMICOLON) {
          {
          setState(111);
          match(SEMICOLON);
          }
        }

        }
        break;
      case 9:
        _localctx = new TryContext(_localctx);
        enterOuterAlt(_localctx, 9);
        {
        setState(114);
        match(TRY);
        setState(115);
        block();
        setState(116);
        match(CATCH);
        setState(117);
        match(LP);
        {
        setState(118);
        match(TYPE);
        setState(119);
        match(ID);
        }
        setState(121);
        match(RP);
        setState(122);
        block();
        }
        break;
      case 10:
        _localctx = new ThrowContext(_localctx);
        enterOuterAlt(_localctx, 10);
        {
        setState(124);
        match(THROW);
        setState(125);
        extstart();
        setState(127);
        _la = _input.LA(1);
        if (_la==SEMICOLON) {
          {
          setState(126);
          match(SEMICOLON);
          }
        }

        }
        break;
      case 11:
        _localctx = new ExprContext(_localctx);
        enterOuterAlt(_localctx, 11);
        {
        setState(129);
        expression(0);
        setState(131);
        _la = _input.LA(1);
        if (_la==SEMICOLON) {
          {
          setState(130);
          match(SEMICOLON);
          }
        }

        }
        break;
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class BlockContext extends ParserRuleContext {
    public BlockContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_block; }
   
    public BlockContext() { }
    public void copyFrom(BlockContext ctx) {
      super.copyFrom(ctx);
    }
  }
  public static class SingleContext extends BlockContext {
    public StatementContext statement() {
      return getRuleContext(StatementContext.class,0);
    }
    public SingleContext(BlockContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitSingle(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class MultipleContext extends BlockContext {
    public TerminalNode LBRACK() { return getToken(PlanAParser.LBRACK, 0); }
    public TerminalNode RBRACK() { return getToken(PlanAParser.RBRACK, 0); }
    public List<StatementContext> statement() {
      return getRuleContexts(StatementContext.class);
    }
    public StatementContext statement(int i) {
      return getRuleContext(StatementContext.class,i);
    }
    public MultipleContext(BlockContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitMultiple(this);
      else return visitor.visitChildren(this);
    }
  }

  public final BlockContext block() throws RecognitionException {
    BlockContext _localctx = new BlockContext(_ctx, getState());
    enterRule(_localctx, 4, RULE_block);
    int _la;
    try {
      setState(144);
      switch (_input.LA(1)) {
      case LBRACK:
        _localctx = new MultipleContext(_localctx);
        enterOuterAlt(_localctx, 1);
        {
        setState(135);
        match(LBRACK);
        setState(139);
        _errHandler.sync(this);
        _la = _input.LA(1);
        while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << LP) | (1L << IF) | (1L << WHILE) | (1L << DO) | (1L << FOR) | (1L << CONTINUE) | (1L << BREAK) | (1L << RETURN) | (1L << NEW) | (1L << TRY) | (1L << THROW) | (1L << BOOLNOT) | (1L << BWNOT) | (1L << ADD) | (1L << SUB) | (1L << INCR) | (1L << DECR))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (OCTAL - 64)) | (1L << (HEX - 64)) | (1L << (INTEGER - 64)) | (1L << (DECIMAL - 64)) | (1L << (STRING - 64)) | (1L << (CHAR - 64)) | (1L << (TRUE - 64)) | (1L << (FALSE - 64)) | (1L << (NULL - 64)) | (1L << (TYPE - 64)) | (1L << (ID - 64)))) != 0)) {
          {
          {
          setState(136);
          statement();
          }
          }
          setState(141);
          _errHandler.sync(this);
          _la = _input.LA(1);
        }
        setState(142);
        match(RBRACK);
        }
        break;
      case LP:
      case IF:
      case WHILE:
      case DO:
      case FOR:
      case CONTINUE:
      case BREAK:
      case RETURN:
      case NEW:
      case TRY:
      case THROW:
      case BOOLNOT:
      case BWNOT:
      case ADD:
      case SUB:
      case INCR:
      case DECR:
      case OCTAL:
      case HEX:
      case INTEGER:
      case DECIMAL:
      case STRING:
      case CHAR:
      case TRUE:
      case FALSE:
      case NULL:
      case TYPE:
      case ID:
        _localctx = new SingleContext(_localctx);
        enterOuterAlt(_localctx, 2);
        {
        setState(143);
        statement();
        }
        break;
      default:
        throw new NoViableAltException(this);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class EmptyContext extends ParserRuleContext {
    public TerminalNode SEMICOLON() { return getToken(PlanAParser.SEMICOLON, 0); }
    public EmptyContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_empty; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitEmpty(this);
      else return visitor.visitChildren(this);
    }
  }

  public final EmptyContext empty() throws RecognitionException {
    EmptyContext _localctx = new EmptyContext(_ctx, getState());
    enterRule(_localctx, 6, RULE_empty);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(146);
      match(SEMICOLON);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class InitializerContext extends ParserRuleContext {
    public DeclarationContext declaration() {
      return getRuleContext(DeclarationContext.class,0);
    }
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public InitializerContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_initializer; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitInitializer(this);
      else return visitor.visitChildren(this);
    }
  }

  public final InitializerContext initializer() throws RecognitionException {
    InitializerContext _localctx = new InitializerContext(_ctx, getState());
    enterRule(_localctx, 8, RULE_initializer);
    try {
      setState(150);
      switch ( getInterpreter().adaptivePredict(_input,17,_ctx) ) {
      case 1:
        enterOuterAlt(_localctx, 1);
        {
        setState(148);
        declaration();
        }
        break;
      case 2:
        enterOuterAlt(_localctx, 2);
        {
        setState(149);
        expression(0);
        }
        break;
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class AfterthoughtContext extends ParserRuleContext {
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public AfterthoughtContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_afterthought; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitAfterthought(this);
      else return visitor.visitChildren(this);
    }
  }

  public final AfterthoughtContext afterthought() throws RecognitionException {
    AfterthoughtContext _localctx = new AfterthoughtContext(_ctx, getState());
    enterRule(_localctx, 10, RULE_afterthought);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(152);
      expression(0);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class DeclarationContext extends ParserRuleContext {
    public DecltypeContext decltype() {
      return getRuleContext(DecltypeContext.class,0);
    }
    public List<DeclvarContext> declvar() {
      return getRuleContexts(DeclvarContext.class);
    }
    public DeclvarContext declvar(int i) {
      return getRuleContext(DeclvarContext.class,i);
    }
    public List<TerminalNode> COMMA() { return getTokens(PlanAParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(PlanAParser.COMMA, i);
    }
    public DeclarationContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_declaration; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitDeclaration(this);
      else return visitor.visitChildren(this);
    }
  }

  public final DeclarationContext declaration() throws RecognitionException {
    DeclarationContext _localctx = new DeclarationContext(_ctx, getState());
    enterRule(_localctx, 12, RULE_declaration);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(154);
      decltype();
      setState(155);
      declvar();
      setState(160);
      _errHandler.sync(this);
      _la = _input.LA(1);
      while (_la==COMMA) {
        {
        {
        setState(156);
        match(COMMA);
        setState(157);
        declvar();
        }
        }
        setState(162);
        _errHandler.sync(this);
        _la = _input.LA(1);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class DecltypeContext extends ParserRuleContext {
    public TerminalNode TYPE() { return getToken(PlanAParser.TYPE, 0); }
    public List<TerminalNode> LBRACE() { return getTokens(PlanAParser.LBRACE); }
    public TerminalNode LBRACE(int i) {
      return getToken(PlanAParser.LBRACE, i);
    }
    public List<TerminalNode> RBRACE() { return getTokens(PlanAParser.RBRACE); }
    public TerminalNode RBRACE(int i) {
      return getToken(PlanAParser.RBRACE, i);
    }
    public DecltypeContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_decltype; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitDecltype(this);
      else return visitor.visitChildren(this);
    }
  }

  public final DecltypeContext decltype() throws RecognitionException {
    DecltypeContext _localctx = new DecltypeContext(_ctx, getState());
    enterRule(_localctx, 14, RULE_decltype);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(163);
      match(TYPE);
      setState(168);
      _errHandler.sync(this);
      _la = _input.LA(1);
      while (_la==LBRACE) {
        {
        {
        setState(164);
        match(LBRACE);
        setState(165);
        match(RBRACE);
        }
        }
        setState(170);
        _errHandler.sync(this);
        _la = _input.LA(1);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class DeclvarContext extends ParserRuleContext {
    public TerminalNode ID() { return getToken(PlanAParser.ID, 0); }
    public TerminalNode ASSIGN() { return getToken(PlanAParser.ASSIGN, 0); }
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public DeclvarContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_declvar; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitDeclvar(this);
      else return visitor.visitChildren(this);
    }
  }

  public final DeclvarContext declvar() throws RecognitionException {
    DeclvarContext _localctx = new DeclvarContext(_ctx, getState());
    enterRule(_localctx, 16, RULE_declvar);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(171);
      match(ID);
      setState(174);
      _la = _input.LA(1);
      if (_la==ASSIGN) {
        {
        setState(172);
        match(ASSIGN);
        setState(173);
        expression(0);
        }
      }

      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class ExpressionContext extends ParserRuleContext {
    public ExpressionContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_expression; }
   
    public ExpressionContext() { }
    public void copyFrom(ExpressionContext ctx) {
      super.copyFrom(ctx);
    }
  }
  public static class CompContext extends ExpressionContext {
    public List<ExpressionContext> expression() {
      return getRuleContexts(ExpressionContext.class);
    }
    public ExpressionContext expression(int i) {
      return getRuleContext(ExpressionContext.class,i);
    }
    public TerminalNode LT() { return getToken(PlanAParser.LT, 0); }
    public TerminalNode LTE() { return getToken(PlanAParser.LTE, 0); }
    public TerminalNode GT() { return getToken(PlanAParser.GT, 0); }
    public TerminalNode GTE() { return getToken(PlanAParser.GTE, 0); }
    public TerminalNode EQ() { return getToken(PlanAParser.EQ, 0); }
    public TerminalNode EQR() { return getToken(PlanAParser.EQR, 0); }
    public TerminalNode NE() { return getToken(PlanAParser.NE, 0); }
    public TerminalNode NER() { return getToken(PlanAParser.NER, 0); }
    public CompContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitComp(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class BoolContext extends ExpressionContext {
    public List<ExpressionContext> expression() {
      return getRuleContexts(ExpressionContext.class);
    }
    public ExpressionContext expression(int i) {
      return getRuleContext(ExpressionContext.class,i);
    }
    public TerminalNode BOOLAND() { return getToken(PlanAParser.BOOLAND, 0); }
    public TerminalNode BOOLOR() { return getToken(PlanAParser.BOOLOR, 0); }
    public BoolContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitBool(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class ConditionalContext extends ExpressionContext {
    public List<ExpressionContext> expression() {
      return getRuleContexts(ExpressionContext.class);
    }
    public ExpressionContext expression(int i) {
      return getRuleContext(ExpressionContext.class,i);
    }
    public TerminalNode COND() { return getToken(PlanAParser.COND, 0); }
    public TerminalNode COLON() { return getToken(PlanAParser.COLON, 0); }
    public ConditionalContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitConditional(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class AssignmentContext extends ExpressionContext {
    public ExtstartContext extstart() {
      return getRuleContext(ExtstartContext.class,0);
    }
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public TerminalNode ASSIGN() { return getToken(PlanAParser.ASSIGN, 0); }
    public TerminalNode AADD() { return getToken(PlanAParser.AADD, 0); }
    public TerminalNode ASUB() { return getToken(PlanAParser.ASUB, 0); }
    public TerminalNode AMUL() { return getToken(PlanAParser.AMUL, 0); }
    public TerminalNode ADIV() { return getToken(PlanAParser.ADIV, 0); }
    public TerminalNode AREM() { return getToken(PlanAParser.AREM, 0); }
    public TerminalNode AAND() { return getToken(PlanAParser.AAND, 0); }
    public TerminalNode AXOR() { return getToken(PlanAParser.AXOR, 0); }
    public TerminalNode AOR() { return getToken(PlanAParser.AOR, 0); }
    public TerminalNode ALSH() { return getToken(PlanAParser.ALSH, 0); }
    public TerminalNode ARSH() { return getToken(PlanAParser.ARSH, 0); }
    public TerminalNode AUSH() { return getToken(PlanAParser.AUSH, 0); }
    public AssignmentContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitAssignment(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class FalseContext extends ExpressionContext {
    public TerminalNode FALSE() { return getToken(PlanAParser.FALSE, 0); }
    public FalseContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitFalse(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class NumericContext extends ExpressionContext {
    public TerminalNode OCTAL() { return getToken(PlanAParser.OCTAL, 0); }
    public TerminalNode HEX() { return getToken(PlanAParser.HEX, 0); }
    public TerminalNode INTEGER() { return getToken(PlanAParser.INTEGER, 0); }
    public TerminalNode DECIMAL() { return getToken(PlanAParser.DECIMAL, 0); }
    public NumericContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitNumeric(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class UnaryContext extends ExpressionContext {
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public TerminalNode BOOLNOT() { return getToken(PlanAParser.BOOLNOT, 0); }
    public TerminalNode BWNOT() { return getToken(PlanAParser.BWNOT, 0); }
    public TerminalNode ADD() { return getToken(PlanAParser.ADD, 0); }
    public TerminalNode SUB() { return getToken(PlanAParser.SUB, 0); }
    public UnaryContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitUnary(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class PrecedenceContext extends ExpressionContext {
    public TerminalNode LP() { return getToken(PlanAParser.LP, 0); }
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public TerminalNode RP() { return getToken(PlanAParser.RP, 0); }
    public PrecedenceContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitPrecedence(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class PreincContext extends ExpressionContext {
    public IncrementContext increment() {
      return getRuleContext(IncrementContext.class,0);
    }
    public ExtstartContext extstart() {
      return getRuleContext(ExtstartContext.class,0);
    }
    public PreincContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitPreinc(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class PostincContext extends ExpressionContext {
    public ExtstartContext extstart() {
      return getRuleContext(ExtstartContext.class,0);
    }
    public IncrementContext increment() {
      return getRuleContext(IncrementContext.class,0);
    }
    public PostincContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitPostinc(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class CastContext extends ExpressionContext {
    public TerminalNode LP() { return getToken(PlanAParser.LP, 0); }
    public DecltypeContext decltype() {
      return getRuleContext(DecltypeContext.class,0);
    }
    public TerminalNode RP() { return getToken(PlanAParser.RP, 0); }
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public CastContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitCast(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class ExternalContext extends ExpressionContext {
    public ExtstartContext extstart() {
      return getRuleContext(ExtstartContext.class,0);
    }
    public ExternalContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitExternal(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class NullContext extends ExpressionContext {
    public TerminalNode NULL() { return getToken(PlanAParser.NULL, 0); }
    public NullContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitNull(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class BinaryContext extends ExpressionContext {
    public List<ExpressionContext> expression() {
      return getRuleContexts(ExpressionContext.class);
    }
    public ExpressionContext expression(int i) {
      return getRuleContext(ExpressionContext.class,i);
    }
    public TerminalNode MUL() { return getToken(PlanAParser.MUL, 0); }
    public TerminalNode DIV() { return getToken(PlanAParser.DIV, 0); }
    public TerminalNode REM() { return getToken(PlanAParser.REM, 0); }
    public TerminalNode ADD() { return getToken(PlanAParser.ADD, 0); }
    public TerminalNode SUB() { return getToken(PlanAParser.SUB, 0); }
    public TerminalNode LSH() { return getToken(PlanAParser.LSH, 0); }
    public TerminalNode RSH() { return getToken(PlanAParser.RSH, 0); }
    public TerminalNode USH() { return getToken(PlanAParser.USH, 0); }
    public TerminalNode BWAND() { return getToken(PlanAParser.BWAND, 0); }
    public TerminalNode BWXOR() { return getToken(PlanAParser.BWXOR, 0); }
    public TerminalNode BWOR() { return getToken(PlanAParser.BWOR, 0); }
    public BinaryContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitBinary(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class CharContext extends ExpressionContext {
    public TerminalNode CHAR() { return getToken(PlanAParser.CHAR, 0); }
    public CharContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitChar(this);
      else return visitor.visitChildren(this);
    }
  }
  public static class TrueContext extends ExpressionContext {
    public TerminalNode TRUE() { return getToken(PlanAParser.TRUE, 0); }
    public TrueContext(ExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitTrue(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ExpressionContext expression() throws RecognitionException {
    return expression(0);
  }

  private ExpressionContext expression(int _p) throws RecognitionException {
    ParserRuleContext _parentctx = _ctx;
    int _parentState = getState();
    ExpressionContext _localctx = new ExpressionContext(_ctx, _parentState);
    ExpressionContext _prevctx = _localctx;
    int _startState = 18;
    enterRecursionRule(_localctx, 18, RULE_expression, _p);
    int _la;
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(204);
      switch ( getInterpreter().adaptivePredict(_input,21,_ctx) ) {
      case 1:
        {
        _localctx = new UnaryContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;

        setState(177);
        _la = _input.LA(1);
        if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << BOOLNOT) | (1L << BWNOT) | (1L << ADD) | (1L << SUB))) != 0)) ) {
        _errHandler.recoverInline(this);
        } else {
          consume();
        }
        setState(178);
        expression(14);
        }
        break;
      case 2:
        {
        _localctx = new CastContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(179);
        match(LP);
        setState(180);
        decltype();
        setState(181);
        match(RP);
        setState(182);
        expression(13);
        }
        break;
      case 3:
        {
        _localctx = new AssignmentContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(184);
        extstart();
        setState(185);
        _la = _input.LA(1);
        if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << ASSIGN) | (1L << AADD) | (1L << ASUB) | (1L << AMUL) | (1L << ADIV) | (1L << AREM) | (1L << AAND) | (1L << AXOR) | (1L << AOR) | (1L << ALSH) | (1L << ARSH) | (1L << AUSH))) != 0)) ) {
        _errHandler.recoverInline(this);
        } else {
          consume();
        }
        setState(186);
        expression(1);
        }
        break;
      case 4:
        {
        _localctx = new PrecedenceContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(188);
        match(LP);
        setState(189);
        expression(0);
        setState(190);
        match(RP);
        }
        break;
      case 5:
        {
        _localctx = new NumericContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(192);
        _la = _input.LA(1);
        if ( !(((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (OCTAL - 64)) | (1L << (HEX - 64)) | (1L << (INTEGER - 64)) | (1L << (DECIMAL - 64)))) != 0)) ) {
        _errHandler.recoverInline(this);
        } else {
          consume();
        }
        }
        break;
      case 6:
        {
        _localctx = new CharContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(193);
        match(CHAR);
        }
        break;
      case 7:
        {
        _localctx = new TrueContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(194);
        match(TRUE);
        }
        break;
      case 8:
        {
        _localctx = new FalseContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(195);
        match(FALSE);
        }
        break;
      case 9:
        {
        _localctx = new NullContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(196);
        match(NULL);
        }
        break;
      case 10:
        {
        _localctx = new PostincContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(197);
        extstart();
        setState(198);
        increment();
        }
        break;
      case 11:
        {
        _localctx = new PreincContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(200);
        increment();
        setState(201);
        extstart();
        }
        break;
      case 12:
        {
        _localctx = new ExternalContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(203);
        extstart();
        }
        break;
      }
      _ctx.stop = _input.LT(-1);
      setState(244);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,23,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          if ( _parseListeners!=null ) triggerExitRuleEvent();
          _prevctx = _localctx;
          {
          setState(242);
          switch ( getInterpreter().adaptivePredict(_input,22,_ctx) ) {
          case 1:
            {
            _localctx = new BinaryContext(new ExpressionContext(_parentctx, _parentState));
            pushNewRecursionContext(_localctx, _startState, RULE_expression);
            setState(206);
            if (!(precpred(_ctx, 12))) throw new FailedPredicateException(this, "precpred(_ctx, 12)");
            setState(207);
            _la = _input.LA(1);
            if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << MUL) | (1L << DIV) | (1L << REM))) != 0)) ) {
            _errHandler.recoverInline(this);
            } else {
              consume();
            }
            setState(208);
            expression(13);
            }
            break;
          case 2:
            {
            _localctx = new BinaryContext(new ExpressionContext(_parentctx, _parentState));
            pushNewRecursionContext(_localctx, _startState, RULE_expression);
            setState(209);
            if (!(precpred(_ctx, 11))) throw new FailedPredicateException(this, "precpred(_ctx, 11)");
            setState(210);
            _la = _input.LA(1);
            if ( !(_la==ADD || _la==SUB) ) {
            _errHandler.recoverInline(this);
            } else {
              consume();
            }
            setState(211);
            expression(12);
            }
            break;
          case 3:
            {
            _localctx = new BinaryContext(new ExpressionContext(_parentctx, _parentState));
            pushNewRecursionContext(_localctx, _startState, RULE_expression);
            setState(212);
            if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
            setState(213);
            _la = _input.LA(1);
            if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << LSH) | (1L << RSH) | (1L << USH))) != 0)) ) {
            _errHandler.recoverInline(this);
            } else {
              consume();
            }
            setState(214);
            expression(11);
            }
            break;
          case 4:
            {
            _localctx = new CompContext(new ExpressionContext(_parentctx, _parentState));
            pushNewRecursionContext(_localctx, _startState, RULE_expression);
            setState(215);
            if (!(precpred(_ctx, 9))) throw new FailedPredicateException(this, "precpred(_ctx, 9)");
            setState(216);
            _la = _input.LA(1);
            if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << LT) | (1L << LTE) | (1L << GT) | (1L << GTE))) != 0)) ) {
            _errHandler.recoverInline(this);
            } else {
              consume();
            }
            setState(217);
            expression(10);
            }
            break;
          case 5:
            {
            _localctx = new CompContext(new ExpressionContext(_parentctx, _parentState));
            pushNewRecursionContext(_localctx, _startState, RULE_expression);
            setState(218);
            if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
            setState(219);
            _la = _input.LA(1);
            if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << EQ) | (1L << EQR) | (1L << NE) | (1L << NER))) != 0)) ) {
            _errHandler.recoverInline(this);
            } else {
              consume();
            }
            setState(220);
            expression(9);
            }
            break;
          case 6:
            {
            _localctx = new BinaryContext(new ExpressionContext(_parentctx, _parentState));
            pushNewRecursionContext(_localctx, _startState, RULE_expression);
            setState(221);
            if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
            setState(222);
            match(BWAND);
            setState(223);
            expression(8);
            }
            break;
          case 7:
            {
            _localctx = new BinaryContext(new ExpressionContext(_parentctx, _parentState));
            pushNewRecursionContext(_localctx, _startState, RULE_expression);
            setState(224);
            if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
            setState(225);
            match(BWXOR);
            setState(226);
            expression(7);
            }
            break;
          case 8:
            {
            _localctx = new BinaryContext(new ExpressionContext(_parentctx, _parentState));
            pushNewRecursionContext(_localctx, _startState, RULE_expression);
            setState(227);
            if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
            setState(228);
            match(BWOR);
            setState(229);
            expression(6);
            }
            break;
          case 9:
            {
            _localctx = new BoolContext(new ExpressionContext(_parentctx, _parentState));
            pushNewRecursionContext(_localctx, _startState, RULE_expression);
            setState(230);
            if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
            setState(231);
            match(BOOLAND);
            setState(232);
            expression(5);
            }
            break;
          case 10:
            {
            _localctx = new BoolContext(new ExpressionContext(_parentctx, _parentState));
            pushNewRecursionContext(_localctx, _startState, RULE_expression);
            setState(233);
            if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
            setState(234);
            match(BOOLOR);
            setState(235);
            expression(4);
            }
            break;
          case 11:
            {
            _localctx = new ConditionalContext(new ExpressionContext(_parentctx, _parentState));
            pushNewRecursionContext(_localctx, _startState, RULE_expression);
            setState(236);
            if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
            setState(237);
            match(COND);
            setState(238);
            expression(0);
            setState(239);
            match(COLON);
            setState(240);
            expression(2);
            }
            break;
          }
          } 
        }
        setState(246);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,23,_ctx);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      unrollRecursionContexts(_parentctx);
    }
    return _localctx;
  }

  public static class ExtstartContext extends ParserRuleContext {
    public ExtprecContext extprec() {
      return getRuleContext(ExtprecContext.class,0);
    }
    public ExtcastContext extcast() {
      return getRuleContext(ExtcastContext.class,0);
    }
    public ExttypeContext exttype() {
      return getRuleContext(ExttypeContext.class,0);
    }
    public ExtvarContext extvar() {
      return getRuleContext(ExtvarContext.class,0);
    }
    public ExtnewContext extnew() {
      return getRuleContext(ExtnewContext.class,0);
    }
    public ExtstringContext extstring() {
      return getRuleContext(ExtstringContext.class,0);
    }
    public ExtstartContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_extstart; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitExtstart(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ExtstartContext extstart() throws RecognitionException {
    ExtstartContext _localctx = new ExtstartContext(_ctx, getState());
    enterRule(_localctx, 20, RULE_extstart);
    try {
      setState(253);
      switch ( getInterpreter().adaptivePredict(_input,24,_ctx) ) {
      case 1:
        enterOuterAlt(_localctx, 1);
        {
        setState(247);
        extprec();
        }
        break;
      case 2:
        enterOuterAlt(_localctx, 2);
        {
        setState(248);
        extcast();
        }
        break;
      case 3:
        enterOuterAlt(_localctx, 3);
        {
        setState(249);
        exttype();
        }
        break;
      case 4:
        enterOuterAlt(_localctx, 4);
        {
        setState(250);
        extvar();
        }
        break;
      case 5:
        enterOuterAlt(_localctx, 5);
        {
        setState(251);
        extnew();
        }
        break;
      case 6:
        enterOuterAlt(_localctx, 6);
        {
        setState(252);
        extstring();
        }
        break;
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class ExtprecContext extends ParserRuleContext {
    public TerminalNode LP() { return getToken(PlanAParser.LP, 0); }
    public TerminalNode RP() { return getToken(PlanAParser.RP, 0); }
    public ExtprecContext extprec() {
      return getRuleContext(ExtprecContext.class,0);
    }
    public ExtcastContext extcast() {
      return getRuleContext(ExtcastContext.class,0);
    }
    public ExttypeContext exttype() {
      return getRuleContext(ExttypeContext.class,0);
    }
    public ExtvarContext extvar() {
      return getRuleContext(ExtvarContext.class,0);
    }
    public ExtnewContext extnew() {
      return getRuleContext(ExtnewContext.class,0);
    }
    public ExtstringContext extstring() {
      return getRuleContext(ExtstringContext.class,0);
    }
    public ExtdotContext extdot() {
      return getRuleContext(ExtdotContext.class,0);
    }
    public ExtbraceContext extbrace() {
      return getRuleContext(ExtbraceContext.class,0);
    }
    public ExtprecContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_extprec; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitExtprec(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ExtprecContext extprec() throws RecognitionException {
    ExtprecContext _localctx = new ExtprecContext(_ctx, getState());
    enterRule(_localctx, 22, RULE_extprec);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(255);
      match(LP);
      setState(262);
      switch ( getInterpreter().adaptivePredict(_input,25,_ctx) ) {
      case 1:
        {
        setState(256);
        extprec();
        }
        break;
      case 2:
        {
        setState(257);
        extcast();
        }
        break;
      case 3:
        {
        setState(258);
        exttype();
        }
        break;
      case 4:
        {
        setState(259);
        extvar();
        }
        break;
      case 5:
        {
        setState(260);
        extnew();
        }
        break;
      case 6:
        {
        setState(261);
        extstring();
        }
        break;
      }
      setState(264);
      match(RP);
      setState(267);
      switch ( getInterpreter().adaptivePredict(_input,26,_ctx) ) {
      case 1:
        {
        setState(265);
        extdot();
        }
        break;
      case 2:
        {
        setState(266);
        extbrace();
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class ExtcastContext extends ParserRuleContext {
    public TerminalNode LP() { return getToken(PlanAParser.LP, 0); }
    public DecltypeContext decltype() {
      return getRuleContext(DecltypeContext.class,0);
    }
    public TerminalNode RP() { return getToken(PlanAParser.RP, 0); }
    public ExtprecContext extprec() {
      return getRuleContext(ExtprecContext.class,0);
    }
    public ExtcastContext extcast() {
      return getRuleContext(ExtcastContext.class,0);
    }
    public ExttypeContext exttype() {
      return getRuleContext(ExttypeContext.class,0);
    }
    public ExtvarContext extvar() {
      return getRuleContext(ExtvarContext.class,0);
    }
    public ExtnewContext extnew() {
      return getRuleContext(ExtnewContext.class,0);
    }
    public ExtstringContext extstring() {
      return getRuleContext(ExtstringContext.class,0);
    }
    public ExtcastContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_extcast; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitExtcast(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ExtcastContext extcast() throws RecognitionException {
    ExtcastContext _localctx = new ExtcastContext(_ctx, getState());
    enterRule(_localctx, 24, RULE_extcast);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(269);
      match(LP);
      setState(270);
      decltype();
      setState(271);
      match(RP);
      setState(278);
      switch ( getInterpreter().adaptivePredict(_input,27,_ctx) ) {
      case 1:
        {
        setState(272);
        extprec();
        }
        break;
      case 2:
        {
        setState(273);
        extcast();
        }
        break;
      case 3:
        {
        setState(274);
        exttype();
        }
        break;
      case 4:
        {
        setState(275);
        extvar();
        }
        break;
      case 5:
        {
        setState(276);
        extnew();
        }
        break;
      case 6:
        {
        setState(277);
        extstring();
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class ExtbraceContext extends ParserRuleContext {
    public TerminalNode LBRACE() { return getToken(PlanAParser.LBRACE, 0); }
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public TerminalNode RBRACE() { return getToken(PlanAParser.RBRACE, 0); }
    public ExtdotContext extdot() {
      return getRuleContext(ExtdotContext.class,0);
    }
    public ExtbraceContext extbrace() {
      return getRuleContext(ExtbraceContext.class,0);
    }
    public ExtbraceContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_extbrace; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitExtbrace(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ExtbraceContext extbrace() throws RecognitionException {
    ExtbraceContext _localctx = new ExtbraceContext(_ctx, getState());
    enterRule(_localctx, 26, RULE_extbrace);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(280);
      match(LBRACE);
      setState(281);
      expression(0);
      setState(282);
      match(RBRACE);
      setState(285);
      switch ( getInterpreter().adaptivePredict(_input,28,_ctx) ) {
      case 1:
        {
        setState(283);
        extdot();
        }
        break;
      case 2:
        {
        setState(284);
        extbrace();
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class ExtdotContext extends ParserRuleContext {
    public TerminalNode DOT() { return getToken(PlanAParser.DOT, 0); }
    public ExtcallContext extcall() {
      return getRuleContext(ExtcallContext.class,0);
    }
    public ExtfieldContext extfield() {
      return getRuleContext(ExtfieldContext.class,0);
    }
    public ExtdotContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_extdot; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitExtdot(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ExtdotContext extdot() throws RecognitionException {
    ExtdotContext _localctx = new ExtdotContext(_ctx, getState());
    enterRule(_localctx, 28, RULE_extdot);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(287);
      match(DOT);
      setState(290);
      switch ( getInterpreter().adaptivePredict(_input,29,_ctx) ) {
      case 1:
        {
        setState(288);
        extcall();
        }
        break;
      case 2:
        {
        setState(289);
        extfield();
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class ExttypeContext extends ParserRuleContext {
    public TerminalNode TYPE() { return getToken(PlanAParser.TYPE, 0); }
    public ExtdotContext extdot() {
      return getRuleContext(ExtdotContext.class,0);
    }
    public ExttypeContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_exttype; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitExttype(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ExttypeContext exttype() throws RecognitionException {
    ExttypeContext _localctx = new ExttypeContext(_ctx, getState());
    enterRule(_localctx, 30, RULE_exttype);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(292);
      match(TYPE);
      setState(293);
      extdot();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class ExtcallContext extends ParserRuleContext {
    public TerminalNode EXTID() { return getToken(PlanAParser.EXTID, 0); }
    public ArgumentsContext arguments() {
      return getRuleContext(ArgumentsContext.class,0);
    }
    public ExtdotContext extdot() {
      return getRuleContext(ExtdotContext.class,0);
    }
    public ExtbraceContext extbrace() {
      return getRuleContext(ExtbraceContext.class,0);
    }
    public ExtcallContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_extcall; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitExtcall(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ExtcallContext extcall() throws RecognitionException {
    ExtcallContext _localctx = new ExtcallContext(_ctx, getState());
    enterRule(_localctx, 32, RULE_extcall);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(295);
      match(EXTID);
      setState(296);
      arguments();
      setState(299);
      switch ( getInterpreter().adaptivePredict(_input,30,_ctx) ) {
      case 1:
        {
        setState(297);
        extdot();
        }
        break;
      case 2:
        {
        setState(298);
        extbrace();
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class ExtvarContext extends ParserRuleContext {
    public TerminalNode ID() { return getToken(PlanAParser.ID, 0); }
    public ExtdotContext extdot() {
      return getRuleContext(ExtdotContext.class,0);
    }
    public ExtbraceContext extbrace() {
      return getRuleContext(ExtbraceContext.class,0);
    }
    public ExtvarContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_extvar; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitExtvar(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ExtvarContext extvar() throws RecognitionException {
    ExtvarContext _localctx = new ExtvarContext(_ctx, getState());
    enterRule(_localctx, 34, RULE_extvar);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(301);
      match(ID);
      setState(304);
      switch ( getInterpreter().adaptivePredict(_input,31,_ctx) ) {
      case 1:
        {
        setState(302);
        extdot();
        }
        break;
      case 2:
        {
        setState(303);
        extbrace();
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class ExtfieldContext extends ParserRuleContext {
    public TerminalNode EXTID() { return getToken(PlanAParser.EXTID, 0); }
    public TerminalNode EXTINTEGER() { return getToken(PlanAParser.EXTINTEGER, 0); }
    public ExtdotContext extdot() {
      return getRuleContext(ExtdotContext.class,0);
    }
    public ExtbraceContext extbrace() {
      return getRuleContext(ExtbraceContext.class,0);
    }
    public ExtfieldContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_extfield; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitExtfield(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ExtfieldContext extfield() throws RecognitionException {
    ExtfieldContext _localctx = new ExtfieldContext(_ctx, getState());
    enterRule(_localctx, 36, RULE_extfield);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(306);
      _la = _input.LA(1);
      if ( !(_la==EXTINTEGER || _la==EXTID) ) {
      _errHandler.recoverInline(this);
      } else {
        consume();
      }
      setState(309);
      switch ( getInterpreter().adaptivePredict(_input,32,_ctx) ) {
      case 1:
        {
        setState(307);
        extdot();
        }
        break;
      case 2:
        {
        setState(308);
        extbrace();
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class ExtnewContext extends ParserRuleContext {
    public TerminalNode NEW() { return getToken(PlanAParser.NEW, 0); }
    public TerminalNode TYPE() { return getToken(PlanAParser.TYPE, 0); }
    public ArgumentsContext arguments() {
      return getRuleContext(ArgumentsContext.class,0);
    }
    public ExtdotContext extdot() {
      return getRuleContext(ExtdotContext.class,0);
    }
    public ExtbraceContext extbrace() {
      return getRuleContext(ExtbraceContext.class,0);
    }
    public List<TerminalNode> LBRACE() { return getTokens(PlanAParser.LBRACE); }
    public TerminalNode LBRACE(int i) {
      return getToken(PlanAParser.LBRACE, i);
    }
    public List<ExpressionContext> expression() {
      return getRuleContexts(ExpressionContext.class);
    }
    public ExpressionContext expression(int i) {
      return getRuleContext(ExpressionContext.class,i);
    }
    public List<TerminalNode> RBRACE() { return getTokens(PlanAParser.RBRACE); }
    public TerminalNode RBRACE(int i) {
      return getToken(PlanAParser.RBRACE, i);
    }
    public ExtnewContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_extnew; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitExtnew(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ExtnewContext extnew() throws RecognitionException {
    ExtnewContext _localctx = new ExtnewContext(_ctx, getState());
    enterRule(_localctx, 38, RULE_extnew);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(311);
      match(NEW);
      setState(312);
      match(TYPE);
      setState(329);
      switch (_input.LA(1)) {
      case LP:
        {
        {
        setState(313);
        arguments();
        setState(316);
        switch ( getInterpreter().adaptivePredict(_input,33,_ctx) ) {
        case 1:
          {
          setState(314);
          extdot();
          }
          break;
        case 2:
          {
          setState(315);
          extbrace();
          }
          break;
        }
        }
        }
        break;
      case LBRACE:
        {
        {
        setState(322); 
        _errHandler.sync(this);
        _alt = 1;
        do {
          switch (_alt) {
          case 1:
            {
            {
            setState(318);
            match(LBRACE);
            setState(319);
            expression(0);
            setState(320);
            match(RBRACE);
            }
            }
            break;
          default:
            throw new NoViableAltException(this);
          }
          setState(324); 
          _errHandler.sync(this);
          _alt = getInterpreter().adaptivePredict(_input,34,_ctx);
        } while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER );
        setState(327);
        switch ( getInterpreter().adaptivePredict(_input,35,_ctx) ) {
        case 1:
          {
          setState(326);
          extdot();
          }
          break;
        }
        }
        }
        break;
      default:
        throw new NoViableAltException(this);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class ExtstringContext extends ParserRuleContext {
    public TerminalNode STRING() { return getToken(PlanAParser.STRING, 0); }
    public ExtdotContext extdot() {
      return getRuleContext(ExtdotContext.class,0);
    }
    public ExtbraceContext extbrace() {
      return getRuleContext(ExtbraceContext.class,0);
    }
    public ExtstringContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_extstring; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitExtstring(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ExtstringContext extstring() throws RecognitionException {
    ExtstringContext _localctx = new ExtstringContext(_ctx, getState());
    enterRule(_localctx, 40, RULE_extstring);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(331);
      match(STRING);
      setState(334);
      switch ( getInterpreter().adaptivePredict(_input,37,_ctx) ) {
      case 1:
        {
        setState(332);
        extdot();
        }
        break;
      case 2:
        {
        setState(333);
        extbrace();
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class ArgumentsContext extends ParserRuleContext {
    public TerminalNode LP() { return getToken(PlanAParser.LP, 0); }
    public TerminalNode RP() { return getToken(PlanAParser.RP, 0); }
    public List<ExpressionContext> expression() {
      return getRuleContexts(ExpressionContext.class);
    }
    public ExpressionContext expression(int i) {
      return getRuleContext(ExpressionContext.class,i);
    }
    public List<TerminalNode> COMMA() { return getTokens(PlanAParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(PlanAParser.COMMA, i);
    }
    public ArgumentsContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_arguments; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitArguments(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ArgumentsContext arguments() throws RecognitionException {
    ArgumentsContext _localctx = new ArgumentsContext(_ctx, getState());
    enterRule(_localctx, 42, RULE_arguments);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      {
      setState(336);
      match(LP);
      setState(345);
      _la = _input.LA(1);
      if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << LP) | (1L << NEW) | (1L << BOOLNOT) | (1L << BWNOT) | (1L << ADD) | (1L << SUB) | (1L << INCR) | (1L << DECR))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (OCTAL - 64)) | (1L << (HEX - 64)) | (1L << (INTEGER - 64)) | (1L << (DECIMAL - 64)) | (1L << (STRING - 64)) | (1L << (CHAR - 64)) | (1L << (TRUE - 64)) | (1L << (FALSE - 64)) | (1L << (NULL - 64)) | (1L << (TYPE - 64)) | (1L << (ID - 64)))) != 0)) {
        {
        setState(337);
        expression(0);
        setState(342);
        _errHandler.sync(this);
        _la = _input.LA(1);
        while (_la==COMMA) {
          {
          {
          setState(338);
          match(COMMA);
          setState(339);
          expression(0);
          }
          }
          setState(344);
          _errHandler.sync(this);
          _la = _input.LA(1);
        }
        }
      }

      setState(347);
      match(RP);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public static class IncrementContext extends ParserRuleContext {
    public TerminalNode INCR() { return getToken(PlanAParser.INCR, 0); }
    public TerminalNode DECR() { return getToken(PlanAParser.DECR, 0); }
    public IncrementContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_increment; }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof PlanAParserVisitor ) return ((PlanAParserVisitor<? extends T>)visitor).visitIncrement(this);
      else return visitor.visitChildren(this);
    }
  }

  public final IncrementContext increment() throws RecognitionException {
    IncrementContext _localctx = new IncrementContext(_ctx, getState());
    enterRule(_localctx, 44, RULE_increment);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(349);
      _la = _input.LA(1);
      if ( !(_la==INCR || _la==DECR) ) {
      _errHandler.recoverInline(this);
      } else {
        consume();
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
    switch (ruleIndex) {
    case 9:
      return expression_sempred((ExpressionContext)_localctx, predIndex);
    }
    return true;
  }
  private boolean expression_sempred(ExpressionContext _localctx, int predIndex) {
    switch (predIndex) {
    case 0:
      return precpred(_ctx, 12);
    case 1:
      return precpred(_ctx, 11);
    case 2:
      return precpred(_ctx, 10);
    case 3:
      return precpred(_ctx, 9);
    case 4:
      return precpred(_ctx, 8);
    case 5:
      return precpred(_ctx, 7);
    case 6:
      return precpred(_ctx, 6);
    case 7:
      return precpred(_ctx, 5);
    case 8:
      return precpred(_ctx, 4);
    case 9:
      return precpred(_ctx, 3);
    case 10:
      return precpred(_ctx, 2);
    }
    return true;
  }

  public static final String _serializedATN =
    "\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\3N\u0162\4\2\t\2\4"+
    "\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
    "\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
    "\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\3\2\6\2\62"+
    "\n\2\r\2\16\2\63\3\2\3\2\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3?\n\3\3\3\3\3"+
    "\3\3\3\3\3\3\3\3\5\3G\n\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3P\n\3\3\3\3\3"+
    "\3\3\5\3U\n\3\3\3\3\3\5\3Y\n\3\3\3\3\3\5\3]\n\3\3\3\3\3\3\3\5\3b\n\3\3"+
    "\3\3\3\5\3f\n\3\3\3\3\3\5\3j\n\3\3\3\3\3\5\3n\n\3\3\3\3\3\3\3\5\3s\n\3"+
    "\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3\u0082\n\3\3\3"+
    "\3\3\5\3\u0086\n\3\5\3\u0088\n\3\3\4\3\4\7\4\u008c\n\4\f\4\16\4\u008f"+
    "\13\4\3\4\3\4\5\4\u0093\n\4\3\5\3\5\3\6\3\6\5\6\u0099\n\6\3\7\3\7\3\b"+
    "\3\b\3\b\3\b\7\b\u00a1\n\b\f\b\16\b\u00a4\13\b\3\t\3\t\3\t\7\t\u00a9\n"+
    "\t\f\t\16\t\u00ac\13\t\3\n\3\n\3\n\5\n\u00b1\n\n\3\13\3\13\3\13\3\13\3"+
    "\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3"+
    "\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\5\13\u00cf\n\13\3\13"+
    "\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13"+
    "\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13"+
    "\3\13\3\13\3\13\3\13\3\13\3\13\3\13\7\13\u00f5\n\13\f\13\16\13\u00f8\13"+
    "\13\3\f\3\f\3\f\3\f\3\f\3\f\5\f\u0100\n\f\3\r\3\r\3\r\3\r\3\r\3\r\3\r"+
    "\5\r\u0109\n\r\3\r\3\r\3\r\5\r\u010e\n\r\3\16\3\16\3\16\3\16\3\16\3\16"+
    "\3\16\3\16\3\16\5\16\u0119\n\16\3\17\3\17\3\17\3\17\3\17\5\17\u0120\n"+
    "\17\3\20\3\20\3\20\5\20\u0125\n\20\3\21\3\21\3\21\3\22\3\22\3\22\3\22"+
    "\5\22\u012e\n\22\3\23\3\23\3\23\5\23\u0133\n\23\3\24\3\24\3\24\5\24\u0138"+
    "\n\24\3\25\3\25\3\25\3\25\3\25\5\25\u013f\n\25\3\25\3\25\3\25\3\25\6\25"+
    "\u0145\n\25\r\25\16\25\u0146\3\25\5\25\u014a\n\25\5\25\u014c\n\25\3\26"+
    "\3\26\3\26\5\26\u0151\n\26\3\27\3\27\3\27\3\27\7\27\u0157\n\27\f\27\16"+
    "\27\u015a\13\27\5\27\u015c\n\27\3\27\3\27\3\30\3\30\3\30\2\3\24\31\2\4"+
    "\6\b\n\f\16\20\22\24\26\30\32\34\36 \"$&(*,.\2\f\4\2\32\33\37 \3\2\65"+
    "@\3\2BE\3\2\34\36\3\2\37 \3\2!#\3\2$\'\3\2(+\3\2MN\3\2\63\64\u01a1\2\61"+
    "\3\2\2\2\4\u0087\3\2\2\2\6\u0092\3\2\2\2\b\u0094\3\2\2\2\n\u0098\3\2\2"+
    "\2\f\u009a\3\2\2\2\16\u009c\3\2\2\2\20\u00a5\3\2\2\2\22\u00ad\3\2\2\2"+
    "\24\u00ce\3\2\2\2\26\u00ff\3\2\2\2\30\u0101\3\2\2\2\32\u010f\3\2\2\2\34"+
    "\u011a\3\2\2\2\36\u0121\3\2\2\2 \u0126\3\2\2\2\"\u0129\3\2\2\2$\u012f"+
    "\3\2\2\2&\u0134\3\2\2\2(\u0139\3\2\2\2*\u014d\3\2\2\2,\u0152\3\2\2\2."+
    "\u015f\3\2\2\2\60\62\5\4\3\2\61\60\3\2\2\2\62\63\3\2\2\2\63\61\3\2\2\2"+
    "\63\64\3\2\2\2\64\65\3\2\2\2\65\66\7\2\2\3\66\3\3\2\2\2\678\7\16\2\28"+
    "9\7\t\2\29:\5\24\13\2:;\7\n\2\2;>\5\6\4\2<=\7\17\2\2=?\5\6\4\2><\3\2\2"+
    "\2>?\3\2\2\2?\u0088\3\2\2\2@A\7\20\2\2AB\7\t\2\2BC\5\24\13\2CF\7\n\2\2"+
    "DG\5\6\4\2EG\5\b\5\2FD\3\2\2\2FE\3\2\2\2G\u0088\3\2\2\2HI\7\21\2\2IJ\5"+
    "\6\4\2JK\7\20\2\2KL\7\t\2\2LM\5\24\13\2MO\7\n\2\2NP\7\r\2\2ON\3\2\2\2"+
    "OP\3\2\2\2P\u0088\3\2\2\2QR\7\22\2\2RT\7\t\2\2SU\5\n\6\2TS\3\2\2\2TU\3"+
    "\2\2\2UV\3\2\2\2VX\7\r\2\2WY\5\24\13\2XW\3\2\2\2XY\3\2\2\2YZ\3\2\2\2Z"+
    "\\\7\r\2\2[]\5\f\7\2\\[\3\2\2\2\\]\3\2\2\2]^\3\2\2\2^a\7\n\2\2_b\5\6\4"+
    "\2`b\5\b\5\2a_\3\2\2\2a`\3\2\2\2b\u0088\3\2\2\2ce\5\16\b\2df\7\r\2\2e"+
    "d\3\2\2\2ef\3\2\2\2f\u0088\3\2\2\2gi\7\23\2\2hj\7\r\2\2ih\3\2\2\2ij\3"+
    "\2\2\2j\u0088\3\2\2\2km\7\24\2\2ln\7\r\2\2ml\3\2\2\2mn\3\2\2\2n\u0088"+
    "\3\2\2\2op\7\25\2\2pr\5\24\13\2qs\7\r\2\2rq\3\2\2\2rs\3\2\2\2s\u0088\3"+
    "\2\2\2tu\7\27\2\2uv\5\6\4\2vw\7\30\2\2wx\7\t\2\2xy\7K\2\2yz\7L\2\2z{\3"+
    "\2\2\2{|\7\n\2\2|}\5\6\4\2}\u0088\3\2\2\2~\177\7\31\2\2\177\u0081\5\26"+
    "\f\2\u0080\u0082\7\r\2\2\u0081\u0080\3\2\2\2\u0081\u0082\3\2\2\2\u0082"+
    "\u0088\3\2\2\2\u0083\u0085\5\24\13\2\u0084\u0086\7\r\2\2\u0085\u0084\3"+
    "\2\2\2\u0085\u0086\3\2\2\2\u0086\u0088\3\2\2\2\u0087\67\3\2\2\2\u0087"+
    "@\3\2\2\2\u0087H\3\2\2\2\u0087Q\3\2\2\2\u0087c\3\2\2\2\u0087g\3\2\2\2"+
    "\u0087k\3\2\2\2\u0087o\3\2\2\2\u0087t\3\2\2\2\u0087~\3\2\2\2\u0087\u0083"+
    "\3\2\2\2\u0088\5\3\2\2\2\u0089\u008d\7\5\2\2\u008a\u008c\5\4\3\2\u008b"+
    "\u008a\3\2\2\2\u008c\u008f\3\2\2\2\u008d\u008b\3\2\2\2\u008d\u008e\3\2"+
    "\2\2\u008e\u0090\3\2\2\2\u008f\u008d\3\2\2\2\u0090\u0093\7\6\2\2\u0091"+
    "\u0093\5\4\3\2\u0092\u0089\3\2\2\2\u0092\u0091\3\2\2\2\u0093\7\3\2\2\2"+
    "\u0094\u0095\7\r\2\2\u0095\t\3\2\2\2\u0096\u0099\5\16\b\2\u0097\u0099"+
    "\5\24\13\2\u0098\u0096\3\2\2\2\u0098\u0097\3\2\2\2\u0099\13\3\2\2\2\u009a"+
    "\u009b\5\24\13\2\u009b\r\3\2\2\2\u009c\u009d\5\20\t\2\u009d\u00a2\5\22"+
    "\n\2\u009e\u009f\7\f\2\2\u009f\u00a1\5\22\n\2\u00a0\u009e\3\2\2\2\u00a1"+
    "\u00a4\3\2\2\2\u00a2\u00a0\3\2\2\2\u00a2\u00a3\3\2\2\2\u00a3\17\3\2\2"+
    "\2\u00a4\u00a2\3\2\2\2\u00a5\u00aa\7K\2\2\u00a6\u00a7\7\7\2\2\u00a7\u00a9"+
    "\7\b\2\2\u00a8\u00a6\3\2\2\2\u00a9\u00ac\3\2\2\2\u00aa\u00a8\3\2\2\2\u00aa"+
    "\u00ab\3\2\2\2\u00ab\21\3\2\2\2\u00ac\u00aa\3\2\2\2\u00ad\u00b0\7L\2\2"+
    "\u00ae\u00af\7\65\2\2\u00af\u00b1\5\24\13\2\u00b0\u00ae\3\2\2\2\u00b0"+
    "\u00b1\3\2\2\2\u00b1\23\3\2\2\2\u00b2\u00b3\b\13\1\2\u00b3\u00b4\t\2\2"+
    "\2\u00b4\u00cf\5\24\13\20\u00b5\u00b6\7\t\2\2\u00b6\u00b7\5\20\t\2\u00b7"+
    "\u00b8\7\n\2\2\u00b8\u00b9\5\24\13\17\u00b9\u00cf\3\2\2\2\u00ba\u00bb"+
    "\5\26\f\2\u00bb\u00bc\t\3\2\2\u00bc\u00bd\5\24\13\3\u00bd\u00cf\3\2\2"+
    "\2\u00be\u00bf\7\t\2\2\u00bf\u00c0\5\24\13\2\u00c0\u00c1\7\n\2\2\u00c1"+
    "\u00cf\3\2\2\2\u00c2\u00cf\t\4\2\2\u00c3\u00cf\7G\2\2\u00c4\u00cf\7H\2"+
    "\2\u00c5\u00cf\7I\2\2\u00c6\u00cf\7J\2\2\u00c7\u00c8\5\26\f\2\u00c8\u00c9"+
    "\5.\30\2\u00c9\u00cf\3\2\2\2\u00ca\u00cb\5.\30\2\u00cb\u00cc\5\26\f\2"+
    "\u00cc\u00cf\3\2\2\2\u00cd\u00cf\5\26\f\2\u00ce\u00b2\3\2\2\2\u00ce\u00b5"+
    "\3\2\2\2\u00ce\u00ba\3\2\2\2\u00ce\u00be\3\2\2\2\u00ce\u00c2\3\2\2\2\u00ce"+
    "\u00c3\3\2\2\2\u00ce\u00c4\3\2\2\2\u00ce\u00c5\3\2\2\2\u00ce\u00c6\3\2"+
    "\2\2\u00ce\u00c7\3\2\2\2\u00ce\u00ca\3\2\2\2\u00ce\u00cd\3\2\2\2\u00cf"+
    "\u00f6\3\2\2\2\u00d0\u00d1\f\16\2\2\u00d1\u00d2\t\5\2\2\u00d2\u00f5\5"+
    "\24\13\17\u00d3\u00d4\f\r\2\2\u00d4\u00d5\t\6\2\2\u00d5\u00f5\5\24\13"+
    "\16\u00d6\u00d7\f\f\2\2\u00d7\u00d8\t\7\2\2\u00d8\u00f5\5\24\13\r\u00d9"+
    "\u00da\f\13\2\2\u00da\u00db\t\b\2\2\u00db\u00f5\5\24\13\f\u00dc\u00dd"+
    "\f\n\2\2\u00dd\u00de\t\t\2\2\u00de\u00f5\5\24\13\13\u00df\u00e0\f\t\2"+
    "\2\u00e0\u00e1\7,\2\2\u00e1\u00f5\5\24\13\n\u00e2\u00e3\f\b\2\2\u00e3"+
    "\u00e4\7-\2\2\u00e4\u00f5\5\24\13\t\u00e5\u00e6\f\7\2\2\u00e6\u00e7\7"+
    ".\2\2\u00e7\u00f5\5\24\13\b\u00e8\u00e9\f\6\2\2\u00e9\u00ea\7/\2\2\u00ea"+
    "\u00f5\5\24\13\7\u00eb\u00ec\f\5\2\2\u00ec\u00ed\7\60\2\2\u00ed\u00f5"+
    "\5\24\13\6\u00ee\u00ef\f\4\2\2\u00ef\u00f0\7\61\2\2\u00f0\u00f1\5\24\13"+
    "\2\u00f1\u00f2\7\62\2\2\u00f2\u00f3\5\24\13\4\u00f3\u00f5\3\2\2\2\u00f4"+
    "\u00d0\3\2\2\2\u00f4\u00d3\3\2\2\2\u00f4\u00d6\3\2\2\2\u00f4\u00d9\3\2"+
    "\2\2\u00f4\u00dc\3\2\2\2\u00f4\u00df\3\2\2\2\u00f4\u00e2\3\2\2\2\u00f4"+
    "\u00e5\3\2\2\2\u00f4\u00e8\3\2\2\2\u00f4\u00eb\3\2\2\2\u00f4\u00ee\3\2"+
    "\2\2\u00f5\u00f8\3\2\2\2\u00f6\u00f4\3\2\2\2\u00f6\u00f7\3\2\2\2\u00f7"+
    "\25\3\2\2\2\u00f8\u00f6\3\2\2\2\u00f9\u0100\5\30\r\2\u00fa\u0100\5\32"+
    "\16\2\u00fb\u0100\5 \21\2\u00fc\u0100\5$\23\2\u00fd\u0100\5(\25\2\u00fe"+
    "\u0100\5*\26\2\u00ff\u00f9\3\2\2\2\u00ff\u00fa\3\2\2\2\u00ff\u00fb\3\2"+
    "\2\2\u00ff\u00fc\3\2\2\2\u00ff\u00fd\3\2\2\2\u00ff\u00fe\3\2\2\2\u0100"+
    "\27\3\2\2\2\u0101\u0108\7\t\2\2\u0102\u0109\5\30\r\2\u0103\u0109\5\32"+
    "\16\2\u0104\u0109\5 \21\2\u0105\u0109\5$\23\2\u0106\u0109\5(\25\2\u0107"+
    "\u0109\5*\26\2\u0108\u0102\3\2\2\2\u0108\u0103\3\2\2\2\u0108\u0104\3\2"+
    "\2\2\u0108\u0105\3\2\2\2\u0108\u0106\3\2\2\2\u0108\u0107\3\2\2\2\u0109"+
    "\u010a\3\2\2\2\u010a\u010d\7\n\2\2\u010b\u010e\5\36\20\2\u010c\u010e\5"+
    "\34\17\2\u010d\u010b\3\2\2\2\u010d\u010c\3\2\2\2\u010d\u010e\3\2\2\2\u010e"+
    "\31\3\2\2\2\u010f\u0110\7\t\2\2\u0110\u0111\5\20\t\2\u0111\u0118\7\n\2"+
    "\2\u0112\u0119\5\30\r\2\u0113\u0119\5\32\16\2\u0114\u0119\5 \21\2\u0115"+
    "\u0119\5$\23\2\u0116\u0119\5(\25\2\u0117\u0119\5*\26\2\u0118\u0112\3\2"+
    "\2\2\u0118\u0113\3\2\2\2\u0118\u0114\3\2\2\2\u0118\u0115\3\2\2\2\u0118"+
    "\u0116\3\2\2\2\u0118\u0117\3\2\2\2\u0119\33\3\2\2\2\u011a\u011b\7\7\2"+
    "\2\u011b\u011c\5\24\13\2\u011c\u011f\7\b\2\2\u011d\u0120\5\36\20\2\u011e"+
    "\u0120\5\34\17\2\u011f\u011d\3\2\2\2\u011f\u011e\3\2\2\2\u011f\u0120\3"+
    "\2\2\2\u0120\35\3\2\2\2\u0121\u0124\7\13\2\2\u0122\u0125\5\"\22\2\u0123"+
    "\u0125\5&\24\2\u0124\u0122\3\2\2\2\u0124\u0123\3\2\2\2\u0125\37\3\2\2"+
    "\2\u0126\u0127\7K\2\2\u0127\u0128\5\36\20\2\u0128!\3\2\2\2\u0129\u012a"+
    "\7N\2\2\u012a\u012d\5,\27\2\u012b\u012e\5\36\20\2\u012c\u012e\5\34\17"+
    "\2\u012d\u012b\3\2\2\2\u012d\u012c\3\2\2\2\u012d\u012e\3\2\2\2\u012e#"+
    "\3\2\2\2\u012f\u0132\7L\2\2\u0130\u0133\5\36\20\2\u0131\u0133\5\34\17"+
    "\2\u0132\u0130\3\2\2\2\u0132\u0131\3\2\2\2\u0132\u0133\3\2\2\2\u0133%"+
    "\3\2\2\2\u0134\u0137\t\n\2\2\u0135\u0138\5\36\20\2\u0136\u0138\5\34\17"+
    "\2\u0137\u0135\3\2\2\2\u0137\u0136\3\2\2\2\u0137\u0138\3\2\2\2\u0138\'"+
    "\3\2\2\2\u0139\u013a\7\26\2\2\u013a\u014b\7K\2\2\u013b\u013e\5,\27\2\u013c"+
    "\u013f\5\36\20\2\u013d\u013f\5\34\17\2\u013e\u013c\3\2\2\2\u013e\u013d"+
    "\3\2\2\2\u013e\u013f\3\2\2\2\u013f\u014c\3\2\2\2\u0140\u0141\7\7\2\2\u0141"+
    "\u0142\5\24\13\2\u0142\u0143\7\b\2\2\u0143\u0145\3\2\2\2\u0144\u0140\3"+
    "\2\2\2\u0145\u0146\3\2\2\2\u0146\u0144\3\2\2\2\u0146\u0147\3\2\2\2\u0147"+
    "\u0149\3\2\2\2\u0148\u014a\5\36\20\2\u0149\u0148\3\2\2\2\u0149\u014a\3"+
    "\2\2\2\u014a\u014c\3\2\2\2\u014b\u013b\3\2\2\2\u014b\u0144\3\2\2\2\u014c"+
    ")\3\2\2\2\u014d\u0150\7F\2\2\u014e\u0151\5\36\20\2\u014f\u0151\5\34\17"+
    "\2\u0150\u014e\3\2\2\2\u0150\u014f\3\2\2\2\u0150\u0151\3\2\2\2\u0151+"+
    "\3\2\2\2\u0152\u015b\7\t\2\2\u0153\u0158\5\24\13\2\u0154\u0155\7\f\2\2"+
    "\u0155\u0157\5\24\13\2\u0156\u0154\3\2\2\2\u0157\u015a\3\2\2\2\u0158\u0156"+
    "\3\2\2\2\u0158\u0159\3\2\2\2\u0159\u015c\3\2\2\2\u015a\u0158\3\2\2\2\u015b"+
    "\u0153\3\2\2\2\u015b\u015c\3\2\2\2\u015c\u015d\3\2\2\2\u015d\u015e\7\n"+
    "\2\2\u015e-\3\2\2\2\u015f\u0160\t\13\2\2\u0160/\3\2\2\2*\63>FOTX\\aei"+
    "mr\u0081\u0085\u0087\u008d\u0092\u0098\u00a2\u00aa\u00b0\u00ce\u00f4\u00f6"+
    "\u00ff\u0108\u010d\u0118\u011f\u0124\u012d\u0132\u0137\u013e\u0146\u0149"+
    "\u014b\u0150\u0158\u015b";
  public static final ATN _ATN =
    new ATNDeserializer().deserialize(_serializedATN.toCharArray());
  static {
    _decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
    for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
      _decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
    }
  }
}
