package dev.spiritstudios.mojank.meow;

import dev.spiritstudios.mojank.internal.EmptyVariables;
import dev.spiritstudios.mojank.meow.compile.Linker;
import dev.spiritstudios.mojank.meow.compile.MeowBootstraps;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

/**
 * Variable storage for Molang functions.
 * <p>
 * For convenience, this implements a map.
 */
@ApiStatus.NonExtendable
public interface Variables extends @Linker.Hidden Map<String, Object> {
	/**
	 * Cached null-returning handle. Used for missing fields.
	 */
	@Linker.Hidden
	MethodHandle NULL_HANDLE = MethodHandles.zero(Object.class);
	/**
	 * Cached null-returning call site. Used for missing fields.
	 */
	@Linker.Hidden
	CallSite NULL_CALL_SITE = new ConstantCallSite(NULL_HANDLE);


	/**
	 * {@inheritDoc}
	 *
	 * @implNote Hardcoded to field count.
	 */
	@Linker.Hidden
	@Override
	int size();

	/**
	 * {@inheritDoc}
	 *
	 * @implNote Unless {@link EmptyVariables}, will always return false.
	 */
	@Linker.Hidden
	@Override
	default boolean isEmpty() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @param key The field for this class.
	 * @implNote Delegates to {@link #keySet()}
	 */
	@Linker.Hidden
	@Override
	@SuppressWarnings({"RedundantCollectionOperation", "SuspiciousMethodCalls"})
	default boolean containsKey(Object key) {
		return this.keySet().contains(key);
	}

	@Linker.Hidden
	@Override
	boolean containsValue(Object value);

	/**
	 * {@inheritDoc}
	 *
	 * @param key The field for this class.
	 */
	@Linker.Hidden
	@Override
	Object get(Object key);

	/**
	 * {@inheritDoc}
	 *
	 * @param key The field for this class.
	 * @throws IllegalArgumentException When the given key does not exist as a field.
	 */
	@Linker.Hidden
	@Nullable
	@Override
	Object put(String key, Object value);

	/**
	 * As this "map" cannot ever remove keys, this operation is unsupported.
	 */
	@Linker.Hidden
	@Override
	default Object remove(Object key) {
		throw new UnsupportedOperationException();
	}

	/**
	 * As this "map" cannot ever be cleared, this operation is unsupported.
	 */
	@Linker.Hidden
	@Override
	default void clear() {
		throw new UnsupportedOperationException();
	}

	/**
	 * A {@link CallSite} that is permanently bound to a getter of the given field at the class level.
	 * <p>
	 * If the backing field is a polymorphic union, the call site may be a dynamic invoker instead.
	 * <p>
	 * The given CallSite does not permanently bind to this instance.
	 *
	 * @param key The field to bind this CallSite to.
	 * @return A global-use CallSite permanently bound to the class's field.
	 * @implSpec This must be implemented as a static method with the same signature, with this as the bridge.
	 * @implNote Invoked by {@link MeowBootstraps#get(MethodHandles.Lookup, String, MethodType)}
	 */
	@Linker.Hidden
	CallSite getCallSiteOf(String key);

	/**
	 * A {@link CallSite} that is permanently bound to a setter of the given field at the class level.
	 * <p>
	 * If the backing field is a polymorphic union, the call site may be a dynamic invoker instead.
	 * <p>
	 * The given CallSite does not permanently bind to this instance.
	 *
	 * @param key The field to bind this CallSite to.
	 * @return A global-use CallSite permanently bound to the class's field.
	 * @throws IllegalArgumentException If the field is not declared in this class.
	 * @implSpec This must be implemented as a static method with the same signature, with this as the bridge.
	 * @implNote Invoked by {@link MeowBootstraps#set(MethodHandles.Lookup, String, MethodType)}
	 */
	@Linker.Hidden
	CallSite setCallSiteOf(String key);

	/**
	 * A {@link MethodHandle} that is permanently bound to this instance's getter of a given field.
	 * <p>
	 * If the backing field is a polymorphic union, the handle may be a dynamic invoker instead.
	 *
	 * @param key The field to bind this MethodHandle to.
	 * @return An instance-use MethodHandle permanently bound to this instance's field.
	 * @implNote Invoked by {@link MeowBootstraps}
	 * @apiNote It is not recommended to permanently bind to these handles in a {@link CallSite}.
	 */
	@Linker.Hidden
	MethodHandle getGetter(String key);

	/**
	 * A {@link MethodHandle} that is permanently bound to this instance's setter of a given field.
	 * <p>
	 * If the backing field is a polymorphic union, the handle may be a dynamic invoker instead.
	 *
	 * @param key The field to bind this MethodHandle to.
	 * @return An instance-use MethodHandle permanently bound to this instance's field.
	 * @throws IllegalArgumentException If the field is not declared in this class.
	 * @implNote Invoked by {@link MeowBootstraps}
	 * @apiNote It is not recommended to permanently bind to these handles in a {@link CallSite}.
	 */
	@Linker.Hidden
	MethodHandle getSetter(String key);


}
