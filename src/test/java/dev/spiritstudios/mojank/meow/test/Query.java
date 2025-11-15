package dev.spiritstudios.mojank.meow.test;

/**
 * @author Ampflower
 * @since ${version}
 **/
public class Query {
	public float anim_time = 5f, life_time;

	public Vec3 pos = new Vec3();

	public float[] array_test = new float[] {
		1F,
		2F,
		4F
	};

	public boolean test_bool = false;
	public boolean test_bool2 = true;
	public final boolean test_bool_false = false;
	public final boolean test_bool_true = true;

	public static class Vec3 {
		public float x;
		public float y;
		public float z;
	}
}
