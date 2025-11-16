package dev.spiritstudios.mojank;

import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.test.Context;
import dev.spiritstudios.mojank.meow.test.MolangMath;
import dev.spiritstudios.mojank.meow.test.Query;
import org.junit.jupiter.api.Test;

import static dev.spiritstudios.mojank.Assertions.assertEvalEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompilerTests {
	// Symbolic constants
	private static final float FALSE = 0F;
	private static final float TRUE = 1F;


	@Test
	public void testAlgebra() throws IllegalAccessException {
		assertEvalEquals(11F - 1F, "return 11+-1"); //Blockbench evaluates this as 11-1
		assertEvalEquals(11F + 1F, "return 11-+1"); //Blockbench evaluates this as 11+1

		assertEvalEquals(Float.POSITIVE_INFINITY, "return 1/0");

		assertEvalEquals(
			42F * 3F - 6F / 2F * 6F,
			"42 * 3 - 6 / 2 * 6"
		);

		assertEvalEquals(
			5F,
			"true * 5"
		);

		assertEvalEquals(
			MolangMath.cos((543f * 354.343f) + 1.5f * MolangMath.pi) == MolangMath.sin(543f * 354.343f) ? 1.f : 0.f,
			"""
				temp.a = 543 * 354.343;
				v.b = 1.5;
				v.c = math.sin(temp.a);
				v.d = math.cos(temp.a+v.b*math.pi);
				v.e = v.d == v.c;
				return v.e;
				"""
		);

		// TODO: make an assert failing test harness
		//  Invalid input: `~`, `:`, and `\` should be treated as potential operator symbols.
//		assertEvalEquals(null, //invalid test, this should always fail
//			"""
//				temp.~ = 543 * 354.343;
//				v.: = 1.5;
//				v.\\ = math.sin(temp.~);
//				v.ß = math.cos(temp.~+variable.:*math.pi);
//				v.□ = v.ß == v.\\;
//				return v.□;
//				"""
//		);
	}

	@Test
	public void testStrings() throws IllegalAccessException {
		assertEvalEquals(FALSE, "return 'A' == 'a'");
		assertEvalEquals(FALSE, "return ' ' ==''");
	}

	@Test
	public void testEqualities() throws IllegalAccessException {
		var context = new Context();
		var query = new Query();

		// TODO: determine whether this is valid and should have greedy operands, or lazy operands.
		//  PowerShell when asked to do 3 -eq 3 -eq 4 -eq 4 evaluates true.
		//  Python when asked to do 3 == 3 == 4 == 4 evaluates false.
		//  Java when asked to do 3 == 3 == 4 == 4 evaluates casting comparison error.
		assertEvalEquals(
			TRUE,
			"""
				v.hawaii = 7.12092;
				t.cat = 6;
				return (v.hawaii == variable.hawaii == t.cat == temp.cat == q.test_bool == query.test_bool) == true;
				""",
			context, query
		);
		assertEvalEquals(
			TRUE,
			"""
				v.hawaii = 7.12092;
				t.cat = 6;
				return ((v.hawaii == variable.hawaii) == (t.cat == temp.cat) == (q.test_bool == query.test_bool)) == true;
				""",
			context, query
		);
		// Invalid: 1F
		// However, this ends up faulting bad and unclear.
		assertEvalEquals(
			TRUE,
			"""
				v.hawaii = 7.12092;
				t.cat = 6;
				return ((v.hawaii == variable.hawaii) == (t.cat == temp.cat) == (q.test_bool == query.test_bool)) == 1F;
				""",
			context, query
		);
	}

	@Test
	@MeowzersWhatAHorribleFunctionName
	public void testErrorsResultingInZero() throws IllegalAccessException {
		assertEvalEquals(TRUE, "return true[0] == true[0]");
	}

	@Test
	public void testConstables() throws IllegalAccessException {
		assertEvalEquals(42F * 3F - 6F / 2F * 6F, "42 * 3 - 6 / 2 * 6");
		assertEvalEquals(1F, "true || true");
		assertEvalEquals(1F, "true && true");
		assertEvalEquals(1F, "true || false");
		assertEvalEquals(0F, "true && false");
	}

	@Test
	public void testMethodCalls() throws IllegalAccessException {
		assertEvalEquals(MolangMath.sin(1.23F), "math.sin(1.23)", true);
		assertEvalEquals(MolangMath.cos(1.23F), "math.cos(1.23)");
	}

	@Test
	public void testClassFields() throws IllegalAccessException {
		var context = new Context();
		var query = new Query();

		assertEvalEquals(MolangMath.sin(query.anim_time * 1.23F), "math.sin(query.anim_time * 1.23)", context, query);

		assertEvalEquals(
			Util.make(() -> {
				var moo = MolangMath.sin(query.anim_time * 1.23F);
				var baa = MolangMath.cos(query.life_time + 2F);
				return moo * moo + baa;
			}),
			"""
				temp.moo = math.sin(query.anim_time * 1.23);
				temp.baa = math.cos(query.life_time + 2.0);
				return temp.moo * temp.moo + temp.baa;
				""",
			context, query
		);

		assertEvalEquals(
			2F,
			"""
				query.pos.x = 2;
				return query.pos.x;
				""",
			context, query
		);
		assertEquals(2F, query.pos.x);


		assertEvalEquals(
			TRUE,
			"""
				query.test_bool = true;
				return query.test_bool;
				""",
			context, query
		);
		assertTrue(query.test_bool);
	}

	@Test
	public void testLocalEquality() throws IllegalAccessException {
		assertEvalEquals(1.3F, "t.a = 1.3; return t.a");
		assertEvalEquals(1.3F, "temp.a = 1.3; return temp.a");
		assertEvalEquals(1.3F, "t.a = 1.3; return temp.a");
		assertEvalEquals(1.3F, "temp.a = 1.3; return t.a");
	}

	@Test
	public void testVariableEquality() throws IllegalAccessException {
		assertEvalEquals(1.3F, "v.a = 1.3; return v.a");
		assertEvalEquals(1.3F, "variable.a = 1.3; return variable.a");
		assertEvalEquals(1.3F, "v.a = 1.3; return variable.a");
		assertEvalEquals(1.3F, "variable.a = 1.3; return v.a");
	}

	@Test
	public void testStringEquality() throws IllegalAccessException {
		assertEvalEquals(FALSE, "'cat' == 'dog'");
		assertEvalEquals(TRUE, "'cat' == 'cat'");

		assertEvalEquals(TRUE, "'cat' != 'dog'");
		assertEvalEquals(FALSE, "'dog' != 'dog'");
	}

	@Test
	public void testVariableStructs() throws IllegalAccessException {
		assertEvalEquals(
			1.3F,
			"""
				v.a.b = 1.3;
				v.b = v.a;

				return v.b.b;
				"""
		);
	}

	@Test
	public void testScopes() throws IllegalAccessException {
		assertEvalEquals(
			78F,
			"""
				{
				return 78;
				};
				return 45;
				"""
		);

		assertEvalEquals(
			1F,
			"""
				{
				v.scopeNestedOne = 1;
				};
				v.scopeOutSide = v.scopeNestedOne;
				return v.scopeOutSide;
				"""
		);
	}

	@Test
	public void testArrayAccess() throws IllegalAccessException {
		var context = new Context();
		var query = new Query();

		assertEvalEquals(query.array_test[0], "query.array_test[0]", context, query);
		assertEvalEquals(query.array_test[(int) 0.99F], "query.array_test[0.99]", context, query);

		assertEvalEquals(query.array_test[1], "query.array_test[1]", context, query);
		assertEvalEquals(query.array_test[(int) 1.99F], "query.array_test[1.99]", context, query);

		assertEvalEquals(query.array_test[2], "query.array_test[2]", context, query);
		assertEvalEquals(query.array_test[(int) 2.99F], "query.array_test[2.99]", context, query);
	}

	@Test
	public void testLoops() throws IllegalAccessException {
		assertEvalEquals(
			Util.make(() -> {
				var x = 1F;
				for (int i = 0; i < 10; i++) {
					x = x + 1;
				}
				return x;
			}),
			"""
				t.x = 1;
				loop(10, {
				  t.x = t.x + 1;
				});
				return t.x;
				"""
		);

		assertEvalEquals(
			Util.make(() -> {
				var x = 1F;

				for (int i = 0; i < 20; i++) {
					x = x + 1;
					if (x == 10F) break;
				}

				var y = x;

				return y;
			}),
			"""
				t.x = 1;
				loop(20, {
				  t.x = t.x + 1;
				  t.x == 10 ? break;
				});
				t.y = t.x;

				return t.y;
				""" // MANUAL VERIFICATION: Check that slot 5 is reused for both the loop index AND y
		);

		assertEvalEquals(
			Util.make(() -> {
				var i = 0F;
				var a = 0F;

				for (int j = 0; j < 20; j++) {
					i = i + 1;
					if (i == 10) continue;
					a = a + 1;
				}

				return a;
			}),
			"""
				t.i = 0;
				t.a = 0;
				loop(20, {
				  t.i = t.i + 1;
				  t.i == 10 ? continue;
				  t.a = t.a + 1;
				});
				return t.a;
				"""
		);
	}

	@Test
	public void testTernaries() throws IllegalAccessException {
		assertEvalEquals(
			77.7F,
			"""
				v.ramen = 77.7;
				v.cat = -0.0000000000013827;
				return true ? v.ramen : v.cat;
				"""
		);
	}

	@Test
	public void testNullCoalescing() throws IllegalAccessException {
		assertEvalEquals(
			1.2F,
			"""
				v.london = (v.git ?? 1.2);
				return v.london;
				"""
		);

		assertEvalEquals(
			1.2F,
			"""
				v.london = v.git ?? 1.2;
				return v.london;
				"""
		);
	}

	@Test
	public void testFinalFieldReassignments() throws IllegalAccessException {
		var context = new Context();
		var query = new Query();

		assertThrows(
			IllegalArgumentException.class,
			() -> assertEvalEquals(
				TRUE,
				"""
					q.test_bool_true = false;
					return q.test_bool_true;
					""",
				context, query
			)
		);


		assertThrows(
			IllegalArgumentException.class,
			() -> assertEvalEquals(
				TRUE,
				"""
					q.test_bool_false = true;
					return q.test_bool_false;
					""",
				context, query
			)
		);
	}

	@Test
	public void testBooleans() throws IllegalAccessException {
		assertEvalEquals(FALSE, "false");
		assertEvalEquals(TRUE, "true");

		assertEvalEquals(TRUE, "!false");
		assertEvalEquals(FALSE, "!true");
	}

	@Test
	public void testComparisons() throws IllegalAccessException {
		assertEvalEquals(FALSE, "6 > 7");
		assertEvalEquals(TRUE, "6 < 7");

		assertEvalEquals(FALSE, "6 >= 7");
		assertEvalEquals(TRUE, "6 <= 7");

		assertEvalEquals(TRUE, "7 >= 7");
		assertEvalEquals(TRUE, "7 <= 7");
	}

	@Test
	public void testLogicalOperators() throws IllegalAccessException {
		assertEvalEquals(TRUE, "true || true || true");
		assertEvalEquals(TRUE, "false || true || false");
		assertEvalEquals(FALSE, "false || false || false");

		assertEvalEquals(TRUE, "true && true && true");
		assertEvalEquals(FALSE, "false && true && false");
		assertEvalEquals(FALSE, "false && false && false");
	}

	@Test
	public void testUnionTypes() throws IllegalAccessException {
		assertEvalEquals(
			15F, """
				variable.a = 'meow';
				v.a = 15;
				return v.a;
				"""
		);

		assertEvalEquals(
			17F, """
				v.a = 15;
				v.a = 'meow';
				v.a = 17;
				return v.a;
				"""
		);

		assertEvalEquals(
			TRUE, """
				v.a = 15;
				v.b = 'dog';
				v.a = 'cat';
				v.b = 19;
				v.b = 'cat';
				return v.a == v.b;
				"""
		);
	}
}
