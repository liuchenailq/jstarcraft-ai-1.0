package com.jstarcraft.ai.data.module;

import java.util.Iterator;
import java.util.List;

import com.jstarcraft.ai.data.DataInstance;
import com.jstarcraft.ai.data.exception.DataCapacityException;
import com.jstarcraft.ai.data.exception.DataCursorException;
import com.jstarcraft.ai.data.exception.DataException;
import com.jstarcraft.ai.utility.FloatArray;
import com.jstarcraft.ai.utility.IntegerArray;
import com.jstarcraft.core.utility.KeyValue;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatSortedMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;

/**
 * 稠密模块
 * 
 * @author Birdy
 *
 */
public class DenseModule extends AbstractModule {

	/** 离散特征 */
	private IntegerArray[] discreteValues;

	/** 连续特征 */
	private FloatArray[] continuousValues;

	public DenseModule(String name, List<KeyValue<KeyValue<String, Boolean>, Integer>> definition, int capacity) {
		super(name, definition, capacity);
		this.discreteValues = new IntegerArray[discreteOrder];
		for (int index = 0; index < discreteOrder; index++) {
			this.discreteValues[index] = new IntegerArray(1000, capacity);
		}
		this.continuousValues = new FloatArray[continuousOrder];
		for (int index = 0; index < continuousOrder; index++) {
			this.continuousValues[index] = new FloatArray(1000, capacity);
		}
	}

	IntegerArray[] getDiscreteValues() {
		return discreteValues;
	}

	FloatArray[] getContinuousValues() {
		return continuousValues;
	}

	@Override
	public void associateInstance(Int2IntSortedMap discreteFeatures, Int2FloatSortedMap continuousFeatures, int discreteMark, float continuousMark) {
		if (capacity == size) {
			throw new DataCapacityException();
		}
		if (discreteFeatures.firstIntKey() < 0 || discreteFeatures.lastIntKey() >= discreteOrder) {
			throw new DataException();
		}
		if (continuousFeatures.firstIntKey() < 0 || continuousFeatures.lastIntKey() >= continuousOrder) {
			throw new DataException();
		}
		assert discreteOrder == discreteFeatures.size();
		assert continuousOrder == continuousFeatures.size();
		for (Int2IntMap.Entry term : discreteFeatures.int2IntEntrySet()) {
			discreteValues[term.getIntKey()].associateData(term.getIntValue());
		}
		for (Int2FloatMap.Entry term : continuousFeatures.int2FloatEntrySet()) {
			continuousValues[term.getIntKey()].associateData(term.getFloatValue());
		}
		discreteMarks.associateData(discreteMark);
		continuousMarks.associateData(continuousMark);
		size++;
	}

	@Override
	public DataInstance getInstance(int cursor) {
		if (cursor < 0 && cursor >= size) {
			throw new DataCursorException();
		}
		return new DenseInstance(cursor, this);
	}

	@Override
	public int getSize() {
		return size;
	}

	@Override
	public Iterator<DataInstance> iterator() {
		return new DenseModuleIterator();
	}

	private class DenseModuleIterator implements Iterator<DataInstance> {

		private int cursor;

		private DataInstance term = new DenseInstance(cursor, DenseModule.this);

		@Override
		public boolean hasNext() {
			return cursor < size;
		}

		@Override
		public DataInstance next() {
			term.setCursor(cursor++);
			return term;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

}
