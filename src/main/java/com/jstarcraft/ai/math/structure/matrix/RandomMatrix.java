package com.jstarcraft.ai.math.structure.matrix;

import java.util.Iterator;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.math3.util.FastMath;

import com.jstarcraft.ai.environment.EnvironmentContext;
import com.jstarcraft.ai.math.structure.MathAccessor;
import com.jstarcraft.ai.math.structure.MathCalculator;
import com.jstarcraft.ai.math.structure.MathMonitor;
import com.jstarcraft.ai.math.structure.ScalarIterator;
import com.jstarcraft.ai.math.structure.vector.MathVector;
import com.jstarcraft.ai.math.structure.vector.RandomVector;
import com.jstarcraft.ai.math.structure.vector.VectorScalar;
import com.jstarcraft.ai.model.ModelCycle;
import com.jstarcraft.ai.model.ModelDefinition;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2FloatSortedMap;

/**
 * 随机矩阵
 * 
 * @author Birdy
 *
 */
@ModelDefinition(value = { "orientation", "clazz", "keys", "values", "rowSize", "columnSize" })
public class RandomMatrix implements MathMatrix, ModelCycle {

	private boolean orientation;

	private String clazz;

	private int[] keys;

	private float[] values;

	private transient Int2FloatSortedMap keyValues;

	private int rowSize, columnSize;

	private transient WeakHashMap<MathMonitor<MatrixScalar>, Object> monitors = new WeakHashMap<>();

	RandomMatrix() {
	}

	@Override
	public int getElementSize() {
		return keyValues.size();
	}

	@Override
	public int getKnownSize() {
		return getElementSize();
	}

	@Override
	public int getUnknownSize() {
		return rowSize * columnSize - getElementSize();
	}

	@Override
	public ScalarIterator<MatrixScalar> iterateElement(MathCalculator mode, MathAccessor<MatrixScalar>... accessors) {
		switch (mode) {
		case SERIAL: {
			RandomMatrixScalar scalar = new RandomMatrixScalar();
			for (Entry element : keyValues.int2FloatEntrySet()) {
				scalar.update(element);
				for (MathAccessor<MatrixScalar> accessor : accessors) {
					accessor.accessElement(scalar);
				}
			}
			return this;
		}
		default: {
			if (orientation) {
				int size = rowSize;
				EnvironmentContext context = EnvironmentContext.getContext();
				Semaphore semaphore = MathCalculator.getSemaphore();
				for (int index = 0; index < size; index++) {
					int rowIndex = index;
					context.doStructureByAny(index, () -> {
						RandomMatrixScalar scalar = new RandomMatrixScalar();
						int from = rowIndex * columnSize;
						int to = rowIndex * columnSize + columnSize;
						for (Entry element : keyValues.subMap(from, to).int2FloatEntrySet()) {
							scalar.update(element);
							for (MathAccessor<MatrixScalar> accessor : accessors) {
								accessor.accessElement(scalar);
							}
						}
						semaphore.release();
					});
				}
				try {
					semaphore.acquire(size);
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
				return this;
			} else {
				int size = columnSize;
				EnvironmentContext context = EnvironmentContext.getContext();
				Semaphore semaphore = MathCalculator.getSemaphore();
				for (int index = 0; index < size; index++) {
					int columnIndex = index;
					context.doStructureByAny(index, () -> {
						RandomMatrixScalar scalar = new RandomMatrixScalar();
						int from = columnIndex * rowSize;
						int to = columnIndex * rowSize + rowSize;
						for (Entry element : keyValues.subMap(from, to).int2FloatEntrySet()) {
							scalar.update(element);
							for (MathAccessor<MatrixScalar> accessor : accessors) {
								accessor.accessElement(scalar);
							}
						}
						semaphore.release();
					});
				}
				try {
					semaphore.acquire(size);
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
				return this;
			}
		}
		}
	}

	@Override
	public RandomMatrix setValues(float value) {
		if (Float.isNaN(value)) {
			int oldElementSize = keyValues.size();
			int oldKnownSize = getKnownSize();
			int oldUnknownSize = getUnknownSize();
			keyValues.clear();
			int newElementSize = 0;
			int newKnownSize = 0;
			int newUnknownSize = rowSize * columnSize;
			if (oldElementSize != newElementSize) {
				for (MathMonitor<MatrixScalar> monitor : monitors.keySet()) {
					monitor.notifySizeChanged(this, oldElementSize, newElementSize, oldKnownSize, newKnownSize, oldUnknownSize, newUnknownSize);
				}
			}
		} else {
			for (Entry term : keyValues.int2FloatEntrySet()) {
				term.setValue(value);
			}
		}
		return this;
	}

	@Override
	public RandomMatrix scaleValues(float value) {
		for (Entry term : keyValues.int2FloatEntrySet()) {
			term.setValue(term.getFloatValue() * value);
		}
		return this;
	}

	@Override
	public RandomMatrix shiftValues(float value) {
		for (Entry term : keyValues.int2FloatEntrySet()) {
			term.setValue(term.getFloatValue() + value);
		}
		return this;
	}

	@Override
	public MathMatrix addMatrix(MathMatrix matrix, boolean transpose) {
		if (orientation) {
			for (int index = 0, size = getRowSize(); index < size; index++) {
				getRowVector(index).addVector(transpose ? matrix.getColumnVector(index) : matrix.getRowVector(index));
			}
		} else {
			for (int index = 0, size = getColumnSize(); index < size; index++) {
				getColumnVector(index).addVector(transpose ? matrix.getRowVector(index) : matrix.getColumnVector(index));
			}
		}
		return this;
	}

	@Override
	public MathMatrix subtractMatrix(MathMatrix matrix, boolean transpose) {
		if (orientation) {
			for (int index = 0, size = getRowSize(); index < size; index++) {
				getRowVector(index).subtractVector(transpose ? matrix.getColumnVector(index) : matrix.getRowVector(index));
			}
		} else {
			for (int index = 0, size = getColumnSize(); index < size; index++) {
				getColumnVector(index).subtractVector(transpose ? matrix.getRowVector(index) : matrix.getColumnVector(index));
			}
		}
		return this;
	}

	@Override
	public MathMatrix multiplyMatrix(MathMatrix matrix, boolean transpose) {
		if (orientation) {
			for (int index = 0, size = getRowSize(); index < size; index++) {
				getRowVector(index).multiplyVector(transpose ? matrix.getColumnVector(index) : matrix.getRowVector(index));
			}
		} else {
			for (int index = 0, size = getColumnSize(); index < size; index++) {
				getColumnVector(index).multiplyVector(transpose ? matrix.getRowVector(index) : matrix.getColumnVector(index));
			}
		}
		return this;
	}

	@Override
	public MathMatrix divideMatrix(MathMatrix matrix, boolean transpose) {
		if (orientation) {
			for (int index = 0, size = getRowSize(); index < size; index++) {
				getRowVector(index).divideVector(transpose ? matrix.getColumnVector(index) : matrix.getRowVector(index));
			}
		} else {
			for (int index = 0, size = getColumnSize(); index < size; index++) {
				getColumnVector(index).divideVector(transpose ? matrix.getRowVector(index) : matrix.getColumnVector(index));
			}
		}
		return this;
	}

	@Override
	public MathMatrix copyMatrix(MathMatrix matrix, boolean transpose) {
		if (orientation) {
			for (int index = 0, size = getRowSize(); index < size; index++) {
				getRowVector(index).copyVector(transpose ? matrix.getColumnVector(index) : matrix.getRowVector(index));
			}
		} else {
			for (int index = 0, size = getColumnSize(); index < size; index++) {
				getColumnVector(index).copyVector(transpose ? matrix.getRowVector(index) : matrix.getColumnVector(index));
			}
		}
		return this;
	}

	@Override
	public MathMatrix dotProduct(MathMatrix leftMatrix, boolean leftTranspose, MathMatrix rightMatrix, boolean rightTranspose, MathCalculator mode) {
		// TODO 此处可以考虑性能优化.
		// TODO 可能触发元素变更.
		switch (mode) {
		case SERIAL: {
			for (MatrixScalar term : this) {
				int rowIndex = term.getRow();
				int columnIndex = term.getColumn();
				MathVector leftVector = leftTranspose ? leftMatrix.getColumnVector(rowIndex) : leftMatrix.getRowVector(rowIndex);
				MathVector rightVector = rightTranspose ? rightMatrix.getRowVector(columnIndex) : rightMatrix.getColumnVector(columnIndex);
				term.dotProduct(leftVector, rightVector);
			}
			return this;
		}
		default: {
			if (orientation) {
				int size = this.getRowSize();
				EnvironmentContext context = EnvironmentContext.getContext();
				CountDownLatch latch = new CountDownLatch(size);
				for (int index = 0; index < size; index++) {
					int rowIndex = index;
					MathVector rowVector = this.getRowVector(index);
					context.doStructureByAny(index, () -> {
						for (VectorScalar term : rowVector) {
							int columnIndex = term.getIndex();
							MathVector leftVector = leftTranspose ? leftMatrix.getColumnVector(rowIndex) : leftMatrix.getRowVector(rowIndex);
							MathVector rightVector = rightTranspose ? rightMatrix.getRowVector(columnIndex) : rightMatrix.getColumnVector(columnIndex);
							term.dotProduct(leftVector, rightVector);
						}
						latch.countDown();
					});
				}
				try {
					latch.await();
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
				return this;
			} else {
				int size = this.getColumnSize();
				EnvironmentContext context = EnvironmentContext.getContext();
				CountDownLatch latch = new CountDownLatch(size);
				for (int index = 0; index < size; index++) {
					int columnIndex = index;
					MathVector columnVector = this.getColumnVector(index);
					context.doStructureByAny(index, () -> {
						for (VectorScalar term : columnVector) {
							int rowIndex = term.getIndex();
							MathVector leftVector = leftTranspose ? leftMatrix.getColumnVector(rowIndex) : leftMatrix.getRowVector(rowIndex);
							MathVector rightVector = rightTranspose ? rightMatrix.getRowVector(columnIndex) : rightMatrix.getColumnVector(columnIndex);
							term.dotProduct(leftVector, rightVector);
						}
						latch.countDown();
					});
				}
				try {
					latch.await();
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
				return this;
			}
		}
		}
	}

	@Override
	public MathMatrix dotProduct(MathVector rowVector, MathVector columnVector, MathCalculator mode) {
		// TODO 可能触发元素变更.
		switch (mode) {
		case SERIAL: {
			if (orientation) {
				for (VectorScalar term : rowVector) {
					float rowValue = term.getValue();
					MathVector leftVector = this.getRowVector(term.getIndex());
					MathVector rightVector = columnVector;
					int leftIndex = 0, rightIndex = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
					if (leftSize != 0 && rightSize != 0) {
						Iterator<VectorScalar> leftIterator = leftVector.iterator();
						Iterator<VectorScalar> rightIterator = rightVector.iterator();
						VectorScalar leftTerm = leftIterator.next();
						VectorScalar rightTerm = rightIterator.next();
						// 判断两个有序数组中是否存在相同的数字
						while (leftIndex < leftSize && rightIndex < rightSize) {
							if (leftTerm.getIndex() == rightTerm.getIndex()) {
								leftTerm.setValue(rowValue * rightTerm.getValue());
								if (leftIterator.hasNext()) {
									leftTerm = leftIterator.next();
								}
								if (rightIterator.hasNext()) {
									rightTerm = rightIterator.next();
								}
								leftIndex++;
								rightIndex++;
							} else if (leftTerm.getIndex() > rightTerm.getIndex()) {
								if (rightIterator.hasNext()) {
									rightTerm = rightIterator.next();
								}
								rightIndex++;
							} else if (leftTerm.getIndex() < rightTerm.getIndex()) {
								if (leftIterator.hasNext()) {
									leftTerm = leftIterator.next();
								}
								leftIndex++;
							}
						}
					}
				}
				return this;
			} else {
				for (VectorScalar term : columnVector) {
					float columnValue = term.getValue();
					MathVector leftVector = this.getColumnVector(term.getIndex());
					MathVector rightVector = rowVector;
					int leftIndex = 0, rightIndex = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
					if (leftSize != 0 && rightSize != 0) {
						Iterator<VectorScalar> leftIterator = leftVector.iterator();
						Iterator<VectorScalar> rightIterator = rightVector.iterator();
						VectorScalar leftTerm = leftIterator.next();
						VectorScalar rightTerm = rightIterator.next();
						// 判断两个有序数组中是否存在相同的数字
						while (leftIndex < leftSize && rightIndex < rightSize) {
							if (leftTerm.getIndex() == rightTerm.getIndex()) {
								leftTerm.setValue(columnValue * rightTerm.getValue());
								if (leftIterator.hasNext()) {
									leftTerm = leftIterator.next();
								}
								if (rightIterator.hasNext()) {
									rightTerm = rightIterator.next();
								}
								leftIndex++;
								rightIndex++;
							} else if (leftTerm.getIndex() > rightTerm.getIndex()) {
								if (rightIterator.hasNext()) {
									rightTerm = rightIterator.next();
								}
								rightIndex++;
							} else if (leftTerm.getIndex() < rightTerm.getIndex()) {
								if (leftIterator.hasNext()) {
									leftTerm = leftIterator.next();
								}
								leftIndex++;
							}
						}
					}
				}
				return this;
			}
		}
		default: {
			if (orientation) {
				int size = rowVector.getElementSize();
				EnvironmentContext context = EnvironmentContext.getContext();
				CountDownLatch latch = new CountDownLatch(size);
				for (VectorScalar term : rowVector) {
					float rowValue = term.getValue();
					MathVector leftVector = this.getRowVector(term.getIndex());
					MathVector rightVector = columnVector;
					context.doStructureByAny(term.getIndex(), () -> {
						int leftIndex = 0, rightIndex = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
						if (leftSize != 0 && rightSize != 0) {
							Iterator<VectorScalar> leftIterator = leftVector.iterator();
							Iterator<VectorScalar> rightIterator = rightVector.iterator();
							VectorScalar leftTerm = leftIterator.next();
							VectorScalar rightTerm = rightIterator.next();
							// 判断两个有序数组中是否存在相同的数字
							while (leftIndex < leftSize && rightIndex < rightSize) {
								if (leftTerm.getIndex() == rightTerm.getIndex()) {
									leftTerm.setValue(rowValue * rightTerm.getValue());
									if (leftIterator.hasNext()) {
										leftTerm = leftIterator.next();
									}
									if (rightIterator.hasNext()) {
										rightTerm = rightIterator.next();
									}
									leftIndex++;
									rightIndex++;
								} else if (leftTerm.getIndex() > rightTerm.getIndex()) {
									if (rightIterator.hasNext()) {
										rightTerm = rightIterator.next();
									}
									rightIndex++;
								} else if (leftTerm.getIndex() < rightTerm.getIndex()) {
									if (leftIterator.hasNext()) {
										leftTerm = leftIterator.next();
									}
									leftIndex++;
								}
							}
						}
						latch.countDown();
					});
				}
				try {
					latch.await();
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
				return this;
			} else {
				int size = columnVector.getElementSize();
				EnvironmentContext context = EnvironmentContext.getContext();
				CountDownLatch latch = new CountDownLatch(size);
				for (VectorScalar term : columnVector) {
					float columnValue = term.getValue();
					MathVector leftVector = this.getColumnVector(term.getIndex());
					MathVector rightVector = rowVector;
					context.doStructureByAny(term.getIndex(), () -> {
						int leftIndex = 0, rightIndex = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
						if (leftSize != 0 && rightSize != 0) {
							Iterator<VectorScalar> leftIterator = leftVector.iterator();
							Iterator<VectorScalar> rightIterator = rightVector.iterator();
							VectorScalar leftTerm = leftIterator.next();
							VectorScalar rightTerm = rightIterator.next();
							// 判断两个有序数组中是否存在相同的数字
							while (leftIndex < leftSize && rightIndex < rightSize) {
								if (leftTerm.getIndex() == rightTerm.getIndex()) {
									leftTerm.setValue(columnValue * rightTerm.getValue());
									if (leftIterator.hasNext()) {
										leftTerm = leftIterator.next();
									}
									if (rightIterator.hasNext()) {
										rightTerm = rightIterator.next();
									}
									leftIndex++;
									rightIndex++;
								} else if (leftTerm.getIndex() > rightTerm.getIndex()) {
									if (rightIterator.hasNext()) {
										rightTerm = rightIterator.next();
									}
									rightIndex++;
								} else if (leftTerm.getIndex() < rightTerm.getIndex()) {
									if (leftIterator.hasNext()) {
										leftTerm = leftIterator.next();
									}
									leftIndex++;
								}
							}
						}
						latch.countDown();
					});
				}
				try {
					latch.await();
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
				return this;
			}
		}
		}
	}

	@Override
	public MathMatrix accumulateProduct(MathMatrix leftMatrix, boolean leftTranspose, MathMatrix rightMatrix, boolean rightTranspose, MathCalculator mode) {
		// TODO 此处可以考虑性能优化.
		// TODO 可能触发元素变更.
		switch (mode) {
		case SERIAL: {
			for (MatrixScalar term : this) {
				int rowIndex = term.getRow();
				int columnIndex = term.getColumn();
				MathVector leftVector = leftTranspose ? leftMatrix.getColumnVector(rowIndex) : leftMatrix.getRowVector(rowIndex);
				MathVector rightVector = rightTranspose ? rightMatrix.getRowVector(columnIndex) : rightMatrix.getColumnVector(columnIndex);
				term.accumulateProduct(leftVector, rightVector);
			}
			return this;
		}
		default: {
			if (orientation) {
				int size = this.getRowSize();
				EnvironmentContext context = EnvironmentContext.getContext();
				CountDownLatch latch = new CountDownLatch(size);
				for (int index = 0; index < size; index++) {
					int rowIndex = index;
					MathVector rowVector = this.getRowVector(index);
					context.doStructureByAny(index, () -> {
						for (VectorScalar term : rowVector) {
							int columnIndex = term.getIndex();
							MathVector leftVector = leftTranspose ? leftMatrix.getColumnVector(rowIndex) : leftMatrix.getRowVector(rowIndex);
							MathVector rightVector = rightTranspose ? rightMatrix.getRowVector(columnIndex) : rightMatrix.getColumnVector(columnIndex);
							term.accumulateProduct(leftVector, rightVector);
						}
						latch.countDown();
					});
				}
				try {
					latch.await();
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
				return this;
			} else {
				int size = this.getColumnSize();
				EnvironmentContext context = EnvironmentContext.getContext();
				CountDownLatch latch = new CountDownLatch(size);
				for (int index = 0; index < size; index++) {
					int columnIndex = index;
					MathVector columnVector = this.getColumnVector(index);
					context.doStructureByAny(index, () -> {
						for (VectorScalar term : columnVector) {
							int rowIndex = term.getIndex();
							MathVector leftVector = leftTranspose ? leftMatrix.getColumnVector(rowIndex) : leftMatrix.getRowVector(rowIndex);
							MathVector rightVector = rightTranspose ? rightMatrix.getRowVector(columnIndex) : rightMatrix.getColumnVector(columnIndex);
							term.accumulateProduct(leftVector, rightVector);
						}
						latch.countDown();
					});
				}
				try {
					latch.await();
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
				return this;
			}
		}
		}
	}

	@Override
	public MathMatrix accumulateProduct(MathVector rowVector, MathVector columnVector, MathCalculator mode) {
		// TODO 可能触发元素变更.
		switch (mode) {
		case SERIAL: {
			if (orientation) {
				for (VectorScalar term : rowVector) {
					float rowValue = term.getValue();
					MathVector leftVector = this.getRowVector(term.getIndex());
					MathVector rightVector = columnVector;
					int leftIndex = 0, rightIndex = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
					if (leftSize != 0 && rightSize != 0) {
						Iterator<VectorScalar> leftIterator = leftVector.iterator();
						Iterator<VectorScalar> rightIterator = rightVector.iterator();
						VectorScalar leftTerm = leftIterator.next();
						VectorScalar rightTerm = rightIterator.next();
						// 判断两个有序数组中是否存在相同的数字
						while (leftIndex < leftSize && rightIndex < rightSize) {
							if (leftTerm.getIndex() == rightTerm.getIndex()) {
								leftTerm.shiftValue(rowValue * rightTerm.getValue());
								if (leftIterator.hasNext()) {
									leftTerm = leftIterator.next();
								}
								if (rightIterator.hasNext()) {
									rightTerm = rightIterator.next();
								}
								leftIndex++;
								rightIndex++;
							} else if (leftTerm.getIndex() > rightTerm.getIndex()) {
								if (rightIterator.hasNext()) {
									rightTerm = rightIterator.next();
								}
								rightIndex++;
							} else if (leftTerm.getIndex() < rightTerm.getIndex()) {
								if (leftIterator.hasNext()) {
									leftTerm = leftIterator.next();
								}
								leftIndex++;
							}
						}
					}
				}
				return this;
			} else {
				for (VectorScalar term : columnVector) {
					float columnValue = term.getValue();
					MathVector leftVector = this.getColumnVector(term.getIndex());
					MathVector rightVector = rowVector;
					int leftIndex = 0, rightIndex = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
					if (leftSize != 0 && rightSize != 0) {
						Iterator<VectorScalar> leftIterator = leftVector.iterator();
						Iterator<VectorScalar> rightIterator = rightVector.iterator();
						VectorScalar leftTerm = leftIterator.next();
						VectorScalar rightTerm = rightIterator.next();
						// 判断两个有序数组中是否存在相同的数字
						while (leftIndex < leftSize && rightIndex < rightSize) {
							if (leftTerm.getIndex() == rightTerm.getIndex()) {
								leftTerm.shiftValue(columnValue * rightTerm.getValue());
								if (leftIterator.hasNext()) {
									leftTerm = leftIterator.next();
								}
								if (rightIterator.hasNext()) {
									rightTerm = rightIterator.next();
								}
								leftIndex++;
								rightIndex++;
							} else if (leftTerm.getIndex() > rightTerm.getIndex()) {
								if (rightIterator.hasNext()) {
									rightTerm = rightIterator.next();
								}
								rightIndex++;
							} else if (leftTerm.getIndex() < rightTerm.getIndex()) {
								if (leftIterator.hasNext()) {
									leftTerm = leftIterator.next();
								}
								leftIndex++;
							}
						}
					}
				}
				return this;
			}
		}
		default: {
			if (orientation) {
				int size = rowVector.getElementSize();
				EnvironmentContext context = EnvironmentContext.getContext();
				CountDownLatch latch = new CountDownLatch(size);
				for (VectorScalar term : rowVector) {
					float rowValue = term.getValue();
					MathVector leftVector = this.getRowVector(term.getIndex());
					MathVector rightVector = columnVector;
					context.doStructureByAny(term.getIndex(), () -> {
						int leftIndex = 0, rightIndex = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
						if (leftSize != 0 && rightSize != 0) {
							Iterator<VectorScalar> leftIterator = leftVector.iterator();
							Iterator<VectorScalar> rightIterator = rightVector.iterator();
							VectorScalar leftTerm = leftIterator.next();
							VectorScalar rightTerm = rightIterator.next();
							// 判断两个有序数组中是否存在相同的数字
							while (leftIndex < leftSize && rightIndex < rightSize) {
								if (leftTerm.getIndex() == rightTerm.getIndex()) {
									leftTerm.shiftValue(rowValue * rightTerm.getValue());
									if (leftIterator.hasNext()) {
										leftTerm = leftIterator.next();
									}
									if (rightIterator.hasNext()) {
										rightTerm = rightIterator.next();
									}
									leftIndex++;
									rightIndex++;
								} else if (leftTerm.getIndex() > rightTerm.getIndex()) {
									if (rightIterator.hasNext()) {
										rightTerm = rightIterator.next();
									}
									rightIndex++;
								} else if (leftTerm.getIndex() < rightTerm.getIndex()) {
									if (leftIterator.hasNext()) {
										leftTerm = leftIterator.next();
									}
									leftIndex++;
								}
							}
						}
						latch.countDown();
					});
				}
				try {
					latch.await();
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
				return this;
			} else {
				int size = columnVector.getElementSize();
				EnvironmentContext context = EnvironmentContext.getContext();
				CountDownLatch latch = new CountDownLatch(size);
				for (VectorScalar term : columnVector) {
					float columnValue = term.getValue();
					MathVector leftVector = this.getColumnVector(term.getIndex());
					MathVector rightVector = rowVector;
					context.doStructureByAny(term.getIndex(), () -> {
						int leftIndex = 0, rightIndex = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
						if (leftSize != 0 && rightSize != 0) {
							Iterator<VectorScalar> leftIterator = leftVector.iterator();
							Iterator<VectorScalar> rightIterator = rightVector.iterator();
							VectorScalar leftTerm = leftIterator.next();
							VectorScalar rightTerm = rightIterator.next();
							// 判断两个有序数组中是否存在相同的数字
							while (leftIndex < leftSize && rightIndex < rightSize) {
								if (leftTerm.getIndex() == rightTerm.getIndex()) {
									leftTerm.shiftValue(columnValue * rightTerm.getValue());
									if (leftIterator.hasNext()) {
										leftTerm = leftIterator.next();
									}
									if (rightIterator.hasNext()) {
										rightTerm = rightIterator.next();
									}
									leftIndex++;
									rightIndex++;
								} else if (leftTerm.getIndex() > rightTerm.getIndex()) {
									if (rightIterator.hasNext()) {
										rightTerm = rightIterator.next();
									}
									rightIndex++;
								} else if (leftTerm.getIndex() < rightTerm.getIndex()) {
									if (leftIterator.hasNext()) {
										leftTerm = leftIterator.next();
									}
									leftIndex++;
								}
							}
						}
						latch.countDown();
					});
				}
				try {
					latch.await();
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
				return this;
			}
		}
		}
	}

	@Override
	public float getSum(boolean absolute) {
		float sum = 0F;
		if (absolute) {
			for (Entry term : keyValues.int2FloatEntrySet()) {
				sum += FastMath.abs(term.getFloatValue());
			}
		} else {
			for (Entry term : keyValues.int2FloatEntrySet()) {
				sum += term.getFloatValue();
			}
		}
		return sum;
	}

	@Override
	public void attachMonitor(MathMonitor<MatrixScalar> monitor) {
		monitors.put(monitor, null);
	}

	@Override
	public void detachMonitor(MathMonitor<MatrixScalar> monitor) {
		monitors.remove(monitor);
	}

	@Override
	public int getRowSize() {
		return rowSize;
	}

	@Override
	public int getColumnSize() {
		return columnSize;
	}

	@Override
	public RandomVector getRowVector(int rowIndex) {
		if (orientation) {
			int from = rowIndex * columnSize;
			int to = rowIndex * columnSize + columnSize;
			return new RandomVector(rowIndex * columnSize, columnSize, keyValues.subMap(from, to));
		} else {
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public RandomVector getColumnVector(int columnIndex) {
		if (orientation) {
			throw new UnsupportedOperationException();
		} else {
			int from = columnIndex * rowSize;
			int to = columnIndex * rowSize + rowSize;
			return new RandomVector(columnIndex * rowSize, rowSize, keyValues.subMap(from, to));
		}
	}

	@Override
	public boolean isIndexed() {
		return false;
	}

	@Override
	public float getValue(int rowIndex, int columnIndex) {
		if (orientation) {
			return keyValues.get(rowIndex * columnSize + columnIndex);
		} else {
			return keyValues.get(columnIndex * rowSize + rowIndex);
		}
	}

	@Override
	public void setValue(int rowIndex, int columnIndex, float value) {
		if (orientation) {
			keyValues.put(rowIndex * columnSize + columnIndex, value);
		} else {
			keyValues.put(columnIndex * rowSize + rowIndex, value);
		}
	}

	@Override
	public void scaleValue(int rowIndex, int columnIndex, float value) {
		if (orientation) {
			value *= keyValues.get(rowIndex * columnSize + columnIndex);
			keyValues.put(rowIndex * columnSize + columnIndex, value);
		} else {
			value *= keyValues.get(columnIndex * rowSize + rowIndex);
			keyValues.put(columnIndex * rowSize + rowIndex, value);
		}
	}

	@Override
	public void shiftValue(int rowIndex, int columnIndex, float value) {
		if (orientation) {
			value += keyValues.get(rowIndex * columnSize + columnIndex);
			keyValues.put(rowIndex * columnSize + columnIndex, value);
		} else {
			value += keyValues.get(columnIndex * rowSize + rowIndex);
			keyValues.put(columnIndex * rowSize + rowIndex, value);
		}
	}

	@Override
	public void beforeSave() {
		clazz = keyValues.getClass().getName();
		int index = 0;
		keys = new int[keyValues.size()];
		values = new float[keyValues.size()];
		for (Int2FloatMap.Entry term : keyValues.int2FloatEntrySet()) {
			keys[index] = term.getIntKey();
			values[index] = term.getFloatValue();
			index++;
		}
	}

	@Override
	public void afterLoad() {
		try {
			keyValues = (Int2FloatSortedMap) Class.forName(clazz).newInstance();
			for (int index = 0, size = keys.length; index < size; index++) {
				keyValues.put(keys[index], values[index]);
			}
			clazz = null;
			keys = null;
			values = null;
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	@Override
	public boolean equals(Object object) {
		if (this == object)
			return true;
		if (object == null)
			return false;
		if (getClass() != object.getClass())
			return false;
		RandomMatrix that = (RandomMatrix) object;
		EqualsBuilder equal = new EqualsBuilder();
		equal.append(this.keyValues, that.keyValues);
		return equal.isEquals();
	}

	@Override
	public int hashCode() {
		HashCodeBuilder hash = new HashCodeBuilder();
		hash.append(keyValues);
		return hash.toHashCode();
	}

	@Override
	public String toString() {
		return keyValues.toString();
	}

	@Override
	public Iterator<MatrixScalar> iterator() {
		return new RandomMatrixIterator();
	}

	private class RandomMatrixIterator implements Iterator<MatrixScalar> {

		private Iterator<Entry> iterator = keyValues.int2FloatEntrySet().iterator();

		private final RandomMatrixScalar term = new RandomMatrixScalar();

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public MatrixScalar next() {
			term.update(iterator.next());
			return term;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

	private class RandomMatrixScalar implements MatrixScalar {

		private Entry element;

		private void update(Entry element) {
			this.element = element;
		}

		@Override
		public int getRow() {
			return orientation ? element.getIntKey() / columnSize : element.getIntKey() % rowSize;
		}

		@Override
		public int getColumn() {
			return orientation ? element.getIntKey() % columnSize : element.getIntKey() / rowSize;
		}

		@Override
		public float getValue() {
			return element.getFloatValue();
		}

		@Override
		public void scaleValue(float value) {
			element.setValue(element.getFloatValue() * value);
		}

		@Override
		public void setValue(float value) {
			if (Float.isNaN(value)) {
				int oldElementSize = keyValues.size();
				int oldKnownSize = getKnownSize();
				int oldUnknownSize = getUnknownSize();
				int newElementSize = oldElementSize - 1;
				int newKnownSize = oldKnownSize - 1;
				int newUnknownSize = oldUnknownSize + 1;
				keyValues.remove(element.getIntKey());
				for (MathMonitor<MatrixScalar> monitor : monitors.keySet()) {
					monitor.notifySizeChanged(RandomMatrix.this, oldElementSize, newElementSize, oldKnownSize, newKnownSize, oldUnknownSize, newUnknownSize);
				}
			} else {
				element.setValue(value);
			}
		}

		@Override
		public void shiftValue(float value) {
			element.setValue(element.getFloatValue() + value);
		}

	}

	public static RandomMatrix valueOf(boolean orientation, int rowSize, int columnSize, Int2FloatSortedMap keyValues) {
		RandomMatrix instance = new RandomMatrix();
		instance.orientation = orientation;
		instance.rowSize = rowSize;
		instance.columnSize = columnSize;
		instance.keyValues = keyValues;
		return instance;
	}

}
