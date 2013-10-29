package mlia.svm

import scala.annotation.tailrec
import breeze.stats.distributions.Uniform
import breeze.linalg._


object SMO {

  def selectJrand(i: Int, m: Int): Int = {
    @tailrec
    def step(i: Int, j: Int): Int = if (i != j) j else step(i, Uniform(0, m).sample().toInt)
    step(i, Uniform(0, m).sample().toInt)
  }

  def clipAlpha(aj: Double, h: Double, l: Double): Double = if (aj > h) h else if (aj < l) l else aj

  def smoSimple(dataMatIn: Array[Array[Double]], classLabels: Array[Double], c: Double, toler: Double, maxIter: Int) = {

    val dataMat = DenseMatrix(dataMatIn: _*)
    val labelMat = DenseMatrix(classLabels).t

    @tailrec
    def outerLoop(curNum: Int = 0,
                  curAlphas: DenseMatrix[Double] = DenseMatrix.zeros(dataMat.rows, 1),
                  curB: Double = 0): (DenseMatrix[Double], Double) = {
      if (curNum == maxIter) (curAlphas, curB)
      else {
        val (iteAlphas, iteB, changeCount) = Range(0, dataMat.rows).foldLeft(curAlphas, curB, 0) {
          case ((alphas, b, change), i) =>

            // this is our prediction of the class
            val fXi: DenseMatrix[Double] = ((alphas :* labelMat).t * (dataMat * dataMat(i, ::).t): DenseMatrix[Double]) :+ b
            // error = fXi - real class
            val ei = fXi(0, 0) - labelMat(i, 0)

            if ((labelMat(i, 0) * ei < -toler && alphas(i, 0) < c) ||
              (labelMat(i, 0) * ei > toler && alphas(i, 0) > 0)) {
              // enter optimization
              val j = selectJrand(i, dataMat.rows)
              val fXj: DenseMatrix[Double] = ((alphas :* labelMat).t * (dataMat * dataMat(j, ::).t): DenseMatrix[Double]) :+ b
              val ej = fXj(0, 0) - labelMat(j, 0)
              val alphaIold = alphas(i, 0)
              val alphaJold = alphas(j, 0)
              // guarantee alphas stay between 0 and c
              val (low, high) = if (labelMat(i, 0) != labelMat(j, 0)) {
                val dev: Double = alphas(j, 0) - alphas(i, 0)
                (scala.math.max(0.0, dev), scala.math.min(c, c + dev))
              } else {
                val total: Double = alphas(j, 0) + alphas(i, 0)
                (scala.math.max(0.0, total - c), scala.math.min(c, total))
              }

              if (low == high) {
                println(s"L == H[$low]")
                (alphas, b, change)
              } else {
                // calculate optimal amount to change alpha[j]
                val eta1: DenseMatrix[Double] = (dataMat(i, ::) * dataMat(j, ::).t: DenseMatrix[Double]) :* 2.0
                val eta2: DenseMatrix[Double] = eta1 :- dataMat(i, ::) * dataMat(i, ::).t
                val eta3: DenseMatrix[Double] = eta2 :- dataMat(j, ::) * dataMat(j, ::).t
                val eta = eta3(0, 0)
                if (eta >= 0) {
                  println(s"eta[$eta] >= 0")
                  (alphas, b, change)
                } else {
                  alphas(j, 0) -= labelMat(j, 0) * (ei - ej) / eta
                  alphas(j, 0) = clipAlpha(alphas(j, 0), high, low)

                  if ((alphas(j, 0) - alphaJold).abs < 0.00001) {
                    println(s"j not moving enough[${(alphas(j, 0) - alphaJold).abs}]")
                    (alphas, b, change)
                  } else {
                    // update i by same amount as j in opposite direction
                    alphas(i, 0) += (labelMat(j, 0) * labelMat(i, 0) * (alphaJold - alphas(j, 0)))
                    val b1_1 = b - ei - labelMat(i, 0) * (alphas(i, 0) - alphaIold)
                    val b1_2: DenseMatrix[Double] = dataMat(i, ::) * dataMat(i, ::).t
                    val b1_3 = labelMat(j, 0) * (alphas(j, 0) - alphaJold)
                    val b1_4: DenseMatrix[Double] = dataMat(i, ::) * dataMat(j, ::).t
                    val b1 = b1_1 * b1_2(0, 0) - b1_3 * b1_4(0, 0)
                    println(s"b1 = $b1")

                    val b2_1 = b - ej - labelMat(i, 0) * (alphas(i, 0) - alphaIold)
                    val b2_2: DenseMatrix[Double] = dataMat(i, ::) * dataMat(j, ::).t
                    val b2_3 = labelMat(j, 0) * (alphas(j, 0) - alphaJold)
                    val b2_4: DenseMatrix[Double] = dataMat(j, ::) * dataMat(j, ::).t
                    val b2 = b2_1 * b2_2(0, 0) - b2_3 * b2_4(0, 0)
                    println(s"b2 = $b2")

                    val newB = {
                      if (alphas(i, 0) > 0 && alphas(i, 0) < c) b1
                      else if (alphas(j, 0) > 0 && alphas(j, 0) < c) b2
                      else (b1 + b2) / 2.0
                    }
                    println(s"iter: $curNum i:$i, pairs changed ${change + 1}")
                    (alphas, newB, change + 1)
                  }
                }
              }
            } else {
              (alphas, b, change)
            }
        }
        val nextNum = if (changeCount == 0) curNum + 1 else 0
        println(s"iteration number: $nextNum")
        outerLoop(nextNum, iteAlphas, iteB)
      }
    }
    outerLoop(0)
  }
}
