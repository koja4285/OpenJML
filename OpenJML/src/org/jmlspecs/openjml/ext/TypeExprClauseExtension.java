package org.jmlspecs.openjml.ext;

import static com.sun.tools.javac.parser.Tokens.TokenKind.SEMI;
import static com.sun.tools.javac.parser.Tokens.TokenKind.FOR;

import org.jmlspecs.openjml.IJmlClauseType;
import org.jmlspecs.openjml.JmlExtension;
import org.jmlspecs.openjml.JmlTokenKind;
import org.jmlspecs.openjml.JmlTree;
import org.jmlspecs.openjml.JmlTree.JmlMethodClauseExpr;
import org.jmlspecs.openjml.JmlTree.JmlTypeClause;
import org.jmlspecs.openjml.JmlTree.JmlTypeClauseExpr;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.JmlAttr;
import com.sun.tools.javac.parser.JmlParser;
import com.sun.tools.javac.parser.Tokens.TokenKind;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.util.Context;

public class TypeExprClauseExtension extends JmlExtension.TypeClause {

    @Override
    public IJmlClauseType[] clauseTypes() { return new IJmlClauseType[]{
            invariantClause, constraintClause, axiomClause, initiallyClause}; }
    
    public static final String invariantID = "invariant";
    public static final String constraintID = "constraint";
    public static final String axiomID = "axiom";
    public static final String initiallyID = "initially";
    
    public static final IJmlClauseType invariantClause = new TypeClause(invariantID);
    
    public static final IJmlClauseType constraintClause = new TypeClause(constraintID);
    
    public static final IJmlClauseType axiomClause = new TypeClause(axiomID);
    
    public static final IJmlClauseType initiallyClause = new TypeClause(initiallyID);
    
    public static class TypeClause extends IJmlClauseType.TypeClause {
        public TypeClause(String keyword) {
            this.keyword = keyword;
        }

        public boolean oldNoLabelAllowed() { return true; }
        public boolean preOrOldWithLabelAllowed() { return false; }
        
        public 
        JmlTypeClause parse(JCModifiers mods, String keyword, IJmlClauseType clauseType, JmlParser parser) {
            init(parser);
            
            int pp = parser.pos();
            int pe = parser.endPos();
            
            scanner.setJmlKeyword(false);
            
            if (clauseType == constraintClause) {
                JmlTree.JmlTypeClauseConstraint tcl = parser.parseConstraint(mods);
                tcl.source = log.currentSourceFile();
                return tcl;
            } else {
                parser.nextToken();
                JCExpression e = parser.parseExpression();
                scanner.setJmlKeyword(true);
                if (parser.token().kind != SEMI) {
                    parser.jmlerror(parser.pos(), parser.endPos(), "jml.bad.construct",
                            keyword + " declaration");
                    parser.skipThroughSemi();
                } else {
                    parser.nextToken();
                }
                if (mods == null) mods = jmlF.at(pp).Modifiers(0);
                JmlTypeClauseExpr tcl = parser.to(jmlF.at(pp).JmlTypeClauseExpr(mods, keyword, clauseType, e));
                tcl.source = log.currentSourceFile();
                return tcl;
            }
        }
        
        public Type typecheck(JmlAttr attr, JCTree expr, Env<AttrContext> env) {
            // TODO Auto-generated method stub
            return null;
        }
    }
}