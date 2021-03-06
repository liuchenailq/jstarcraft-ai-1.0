package com.jstarcraft.ai.data.module;

import com.jstarcraft.ai.data.ContinuousAccessor;
import com.jstarcraft.ai.data.DataInstance;
import com.jstarcraft.ai.data.DiscreteAccessor;
import com.jstarcraft.ai.utility.FloatArray;
import com.jstarcraft.ai.utility.IntegerArray;

public class SparseInstance implements DataInstance {

	private int cursor;

	/** 离散秩 */
	private int discreteOrder;

	/** 连续秩 */
	private int continuousOrder;

	/** 离散特征 */
	private int[] discreteFeatures;

	private IntegerArray discretePoints;

	private IntegerArray discreteIndexes;

	private IntegerArray discreteValues;

	/** 连续特征 */
	private float[] continuousFeatures;

	private IntegerArray continuousPoints;

	private IntegerArray continuousIndexes;

	private FloatArray continuousValues;

	/** 离散标记 */
	protected IntegerArray discreteMarks;

	/** 连续标记 */
	protected FloatArray continuousMarks;

	SparseInstance(int cursor, SparseModule module) {
		this.cursor = cursor;
		this.discreteOrder = module.getDiscreteOrder();
		this.continuousOrder = module.getContinuousOrder();
		this.discreteFeatures = new int[module.getDiscreteOrder()];
		{
			for (int index = 0, size = module.getDiscreteOrder(); index < size; index++) {
				this.discreteFeatures[index] = DataInstance.defaultInteger;
			}
		}
		this.discretePoints = module.getDiscretePoints();
		this.discreteIndexes = module.getDiscreteIndexes();
		this.discreteValues = module.getDiscreteValues();
		this.continuousFeatures = new float[module.getContinuousOrder()];
		{
			for (int index = 0, size = module.getContinuousOrder(); index < size; index++) {
				this.continuousFeatures[index] = DataInstance.defaultFloat;
			}
		}
		this.continuousPoints = module.getContinuousPoints();
		this.continuousIndexes = module.getContinuousIndexes();
		this.continuousValues = module.getContinuousValues();
		{
			int from = this.discretePoints.getData(this.cursor);
			int to = this.discretePoints.getData(this.cursor + 1);
			for (int position = from; position < to; position++) {
				int index = this.discreteIndexes.getData(position);
				this.discreteFeatures[index] = this.discreteValues.getData(position);
			}
		}
		{
			int from = this.continuousPoints.getData(this.cursor);
			int to = this.continuousPoints.getData(this.cursor + 1);
			for (int position = from; position < to; position++) {
				int index = this.continuousIndexes.getData(position);
				this.continuousFeatures[index] = this.continuousValues.getData(position);
			}
		}
	}

	@Override
	public void setCursor(int cursor) {
		{
			int from = this.discretePoints.getData(this.cursor);
			int to = this.discretePoints.getData(this.cursor + 1);
			for (int current = from; current < to; current++) {
				int index = this.discreteIndexes.getData(current);
				this.discreteFeatures[index] = DataInstance.defaultInteger;
			}
		}
		{
			int from = this.continuousPoints.getData(this.cursor);
			int to = this.continuousPoints.getData(this.cursor + 1);
			for (int current = from; current < to; current++) {
				int index = this.continuousIndexes.getData(current);
				this.continuousFeatures[index] = DataInstance.defaultFloat;
			}
		}
		this.cursor = cursor;
		{
			int from = this.discretePoints.getData(this.cursor);
			int to = this.discretePoints.getData(this.cursor + 1);
			for (int current = from; current < to; current++) {
				int index = this.discreteIndexes.getData(current);
				this.discreteFeatures[index] = this.discreteValues.getData(current);
			}
		}
		{
			int from = this.continuousPoints.getData(this.cursor);
			int to = this.continuousPoints.getData(this.cursor + 1);
			for (int current = from; current < to; current++) {
				int index = this.continuousIndexes.getData(current);
				this.continuousFeatures[index] = this.continuousValues.getData(current);
			}
		}
	}

	@Override
	public int getCursor() {
		return cursor;
	}

	@Override
	public int getDiscreteFeature(int index) {
		return this.discreteFeatures[index];
	}

	@Override
	public float getContinuousFeature(int index) {
		return this.continuousFeatures[index];
	}

	@Override
	public SparseInstance iterateDiscreteFeatures(DiscreteAccessor accessor) {
		int from = this.discretePoints.getData(this.cursor);
		int to = this.discretePoints.getData(this.cursor + 1);
		for (int position = from; position < to; position++) {
			int index = this.discreteIndexes.getData(position);
			accessor.accessorFeature(index, this.discreteFeatures[index]);
		}
		return this;
	}

	@Override
	public SparseInstance iterateContinuousFeatures(ContinuousAccessor accessor) {
		int from = this.continuousPoints.getData(this.cursor);
		int to = this.continuousPoints.getData(this.cursor + 1);
		for (int position = from; position < to; position++) {
			int index = this.continuousIndexes.getData(position);
			accessor.accessorFeature(index, this.continuousFeatures[index]);
		}
		return this;
	}

	@Override
	public int getDiscreteMark() {
		return discreteMarks.getData(cursor);
	}

	@Override
	public float getContinuousMark() {
		return continuousMarks.getData(cursor);
	}

	@Override
	public int getDiscreteOrder() {
		return discreteOrder;
	}

	@Override
	public int getContinuousOrder() {
		return continuousOrder;
	}

}
