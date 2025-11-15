package dev.spiritstudios.mojank;

import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.test.Context;
import dev.spiritstudios.mojank.meow.test.MolangMath;
import dev.spiritstudios.mojank.meow.test.Query;
import org.junit.jupiter.api.Test;

import static dev.spiritstudios.mojank.Assertions.assertEvalEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompilerTests {
	// Symbolic constants
	private static final float FALSE = 0F;
	private static final float TRUE = 1F;


	@Test
	public void testConstables() throws IllegalAccessException {
		assertEvalEquals(42F * 3F - 6F / 2F * 6F, "42 * 3 - 6 / 2 * 6");
		assertEvalEquals(1F, "true || true");
		assertEvalEquals(1F, "true && true");
		assertEvalEquals(1F, "true || false");
		assertEvalEquals(0F, "true && false");
	}

	@Test
	public void testFunctionCalls() throws IllegalAccessException {
		assertEvalEquals(MolangMath.sin(1.23F), "math.sin(1.23)");
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
	}

	@Test
	public void testLocalEquality() throws IllegalAccessException {
		assertEvalEquals(1.3F, "t.a = 1.3; return t.a");
		assertEvalEquals(1.3F, "temp.a = 1.3; return temp.a");
		assertEvalEquals(1.3F, "t.a = 1.3; return temp.a");
		assertEvalEquals(1.3F, "temp.a = 1.3; return t.a");

		assertEvalEquals(1.3F, "T.a = 1.3; return t.a");
		assertEvalEquals(1.3F, "tEmP.a = 1.3; return tEmp.a");
		assertEvalEquals(1.3F, "t.a = 1.3; return temP.a");
		assertEvalEquals(1.3F, "tEmp.a = 1.3; return T.a");
	}

	@Test
	public void testVariableEquality() throws IllegalAccessException {
		assertEvalEquals(1.3F, "v.a = 1.3; return v.a");
		assertEvalEquals(1.3F, "variable.a = 1.3; return variable.a");
		assertEvalEquals(1.3F, "v.a = 1.3; return variable.a");
		assertEvalEquals(1.3F, "variable.a = 1.3; return v.a");

		assertEvalEquals(1.3F, "v.a = 1.3; return V.a");
		assertEvalEquals(1.3F, "varIaBle.a = 1.3; return VAriAble.a");
		assertEvalEquals(1.3F, "V.a = 1.3; return VariabLe.a");
		assertEvalEquals(1.3F, "vAriable.a = 1.3; return V.a");
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
	public void testScopes() throws IllegalAccessException {
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

		assertEvalEquals(
			1F,
			"""
				{
				t.scopeNestedOne = 1;
				};
				t.scopeOutSide = t.scopeNestedOne;
				return t.scopeOutSide;
				"""
		);

		assertEvalEquals(
			78F,
			"""
				{
				return 78;
				};
				return 45;
				"""
		);
	}

	@Test
	public void testNullCoalescing() throws IllegalAccessException {
		assertEvalEquals(
			1.2F,
			"""
				variable.london = variable.git ?? 1.2;
				return variable.london;
				"""
		);

		assertEvalEquals(
			1.2F,
			"""
				variable.london = (variable.git ?? 1.2);
				return variable.london;
				"""
		);
	}


	@Test
	public void testBooleans() throws IllegalAccessException {
		assertEvalEquals(FALSE, "false");
		assertEvalEquals(TRUE, "true");

		assertEvalEquals(TRUE, "!false");
		assertEvalEquals(FALSE, "!true");
	}
}
