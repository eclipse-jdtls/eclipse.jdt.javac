package org.eclipse.jdt.internal.javac.dom;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.core.runtime.ILog;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.internal.compiler.parser.RecoveryScanner;
import org.eclipse.jdt.internal.compiler.parser.Scanner;
import org.eclipse.jdt.internal.compiler.parser.TerminalToken;
import org.eclipse.jdt.internal.core.Annotation;
import org.eclipse.jdt.internal.core.JavaElement;

/**
 * This class is 100% copied from DOMToModelPopulator and should be removed once these
 * methods become more easily accessible.
 */
public class JavacVariableBindingAnnotUtil {
	public static Annotation modelAnnotation(org.eclipse.jdt.core.dom.Annotation domAnnotation, JavaElement parent) {
		IMemberValuePair[] members;
		if (domAnnotation instanceof NormalAnnotation normalAnnotation) {
			members = ((List<MemberValuePair>)normalAnnotation.values()).stream().map(domMemberValuePair -> {
				Entry<Object, Integer> value = memberValueToEntries(domMemberValuePair.getValue());
				return new org.eclipse.jdt.internal.core.MemberValuePair(domMemberValuePair.getName().toString(), value.getKey(), value.getValue());
			}).toArray(IMemberValuePair[]::new);
		} else if (domAnnotation instanceof SingleMemberAnnotation single) {
			Entry<Object, Integer> value = memberValueToEntries(single.getValue());
			members = new IMemberValuePair[] { new org.eclipse.jdt.internal.core.MemberValuePair("value", value.getKey(), value.getValue())}; //$NON-NLS-1$
		} else {
			members = new IMemberValuePair[0];
		}

		return new Annotation(parent, domAnnotation.getTypeName().toString()) {
			@Override
			public IMemberValuePair[] getMemberValuePairs() {
				return members;
			}
		};
	}

	public static Entry<Object, Integer> memberValueToEntries(Expression dom) {
		if (dom == null ||
			dom instanceof NullLiteral ||
			(dom instanceof SimpleName name && (
				"MISSING".equals(name.getIdentifier()) || //$NON-NLS-1$ // better compare with internal SimpleName.MISSING
				Arrays.equals(RecoveryScanner.FAKE_IDENTIFIER, name.getIdentifier().toCharArray())))) {
			return new SimpleEntry<>(null, IMemberValuePair.K_UNKNOWN);
		}
		if (dom instanceof StringLiteral stringValue) {
			try {
				return new SimpleEntry<>(stringValue.getLiteralValue(), IMemberValuePair.K_STRING);
			} catch (IllegalArgumentException e) {
				// lombok oddity, let's ignore
			}
		}
		if (dom instanceof BooleanLiteral booleanValue) {
			return new SimpleEntry<>(booleanValue.booleanValue(), IMemberValuePair.K_BOOLEAN);
		}
		if (dom instanceof CharacterLiteral charValue) {
			return new SimpleEntry<>(charValue.charValue(), IMemberValuePair.K_CHAR);
		}
		if (dom instanceof TypeLiteral typeLiteral) {
			return new SimpleEntry<>(typeLiteral.getType(), IMemberValuePair.K_CLASS);
		}
		if (dom instanceof SimpleName simpleName) {
			return new SimpleEntry<>(simpleName.toString(), IMemberValuePair.K_SIMPLE_NAME);
		}
		if (dom instanceof QualifiedName qualifiedName) {
			return new SimpleEntry<>(qualifiedName.toString(), IMemberValuePair.K_QUALIFIED_NAME);
		}
		if (dom instanceof org.eclipse.jdt.core.dom.Annotation annotation) {
			return new SimpleEntry<>(modelAnnotation(annotation, null), IMemberValuePair.K_ANNOTATION);
		}
		if (dom instanceof ArrayInitializer arrayInitializer) {
			var values = ((List<Expression>)arrayInitializer.expressions()).stream().map(x -> memberValueToEntries(x)).toList();
			var types = values.stream().map(Entry::getValue).distinct().toList();
			return new SimpleEntry<>(values.stream().map(Entry::getKey).toArray(), types.size() == 1 ? types.get(0) : IMemberValuePair.K_UNKNOWN);
		}
		if (dom instanceof NumberLiteral number) {
			String token = number.getToken();
			int type = annotationValuePairType(token);
			Object value = token;
			if ((type == IMemberValuePair.K_LONG && token.endsWith("L")) || //$NON-NLS-1$
				(type == IMemberValuePair.K_FLOAT && token.endsWith("f"))) { //$NON-NLS-1$
				value = token.substring(0, token.length() - 1);
			}
			if (value instanceof String valueString) {
				// I tried using `yield`, but this caused ECJ to throw an AIOOB, preventing compilation
				switch (type) {
					case IMemberValuePair.K_INT: {
						try {
							value =  Integer.parseInt(valueString);
						} catch (NumberFormatException e) {
							type = IMemberValuePair.K_LONG;
							value = Long.parseLong(valueString);
						}
						break;
					}
					case IMemberValuePair.K_LONG: value = Long.parseLong(valueString); break;
					case IMemberValuePair.K_SHORT: value = Short.parseShort(valueString); break;
					case IMemberValuePair.K_BYTE: value = Byte.parseByte(valueString); break;
					case IMemberValuePair.K_FLOAT: value = Float.parseFloat(valueString); break;
					case IMemberValuePair.K_DOUBLE: value = Double.parseDouble(valueString); break;
					default: throw new IllegalArgumentException("Type not (yet?) supported"); //$NON-NLS-1$
				}
			}
			return new SimpleEntry<>(value, type);
		}
		if (dom instanceof PrefixExpression prefixExpression) {
			Expression operand = prefixExpression.getOperand();
			if (!(operand instanceof NumberLiteral) && !(operand instanceof BooleanLiteral)) {
				return new SimpleEntry<>(null, IMemberValuePair.K_UNKNOWN);
			}
			Entry<Object, Integer> entry = memberValueToEntries(prefixExpression.getOperand());
			return new SimpleEntry<>(prefixExpression.getOperator().toString() + entry.getKey(), entry.getValue());
		}
		return new SimpleEntry<>(null, IMemberValuePair.K_UNKNOWN);
	}
	private static int annotationValuePairType(String token) {

		// inspired by NumberLiteral.setToken
		Scanner scanner = new Scanner();
		scanner.setSource(token.toCharArray());
		try {
			TerminalToken tokenType = scanner.getNextToken();
			return switch(tokenType) {
				case TokenNameDoubleLiteral -> IMemberValuePair.K_DOUBLE;
				case TokenNameIntegerLiteral -> IMemberValuePair.K_INT;
				case TokenNameFloatingPointLiteral -> IMemberValuePair.K_FLOAT;
				case TokenNameLongLiteral -> IMemberValuePair.K_LONG;
				case TokenNameMINUS ->
					switch (scanner.getNextToken()) {
						case TokenNameDoubleLiteral -> IMemberValuePair.K_DOUBLE;
						case TokenNameIntegerLiteral -> IMemberValuePair.K_INT;
						case TokenNameFloatingPointLiteral -> IMemberValuePair.K_FLOAT;
						case TokenNameLongLiteral -> IMemberValuePair.K_LONG;
						default -> throw new IllegalArgumentException("Invalid number literal : >" + token + "<"); //$NON-NLS-1$//$NON-NLS-2$
					};
				default -> throw new IllegalArgumentException("Invalid number literal : >" + token + "<"); //$NON-NLS-1$//$NON-NLS-2$
			};
		} catch (InvalidInputException ex) {
			ILog.get().error(ex.getMessage(), ex);
			return IMemberValuePair.K_UNKNOWN;
		}
	}

}
