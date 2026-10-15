#include <stdio.h>
#include <stdlib.h>
#include <math.h>

static int compare_double(const void *a, const void *b) {
    double x = *(const double *)a, y = *(const double *)b;
    return (x > y) - (x < y);
}

static double mean(const double a[], int n) {
    double sum = 0; 
    for (int i = 0; i < n; i++) sum += a[i]; 
    return n ? sum / n : 0;
}

static double median(double a[], int n) {
    if (!n) return 0;
    qsort(a, n, sizeof(double), compare_double);
    return n % 2 ? a[n / 2] : (a[n / 2 - 1] + a[n / 2]) / 2.0;
}

static double variance(const double a[], int n) {
    if (!n) return 0; 
    double m = mean(a, n), sum = 0;
    for (int i = 0; i < n; i++) sum += (a[i] - m) * (a[i] - m);
    return sum / n; /* population variance */
}

static double correlation(const double x[], const double y[], int n) {
    if (n < 2) return 0; 
    double mx = mean(x, n), my = mean(y, n), num = 0, dx = 0, dy = 0;
    for (int i = 0; i < n; i++) {
        num += (x[i] - mx) * (y[i] - my);
        dx += (x[i] - mx) * (x[i] - mx);
        dy += (y[i] - my) * (y[i] - my);
    }
    if (dx == 0 || dy == 0) return 0;
    return num / sqrt(dx * dy);
}

int main() {
    double scores[] = {88.0, 76.0, 61.0, 94.0, 68.0, 82.0};
    double hours[] = {3.0, 2.0, 1.0, 4.0, 2.0, 3.0};
    int n = 6;

    printf("STUDENT PERFORMANCE - C STATISTICS MODULE\n");
    printf("----------------------------------------\n");
    printf("Sample count       : %d\n", n);
    printf("Mean               : %.2f\n", mean(scores, n));
    printf("Median             : %.2f\n", median(scores, n));
    printf("Population variance: %.2f\n", variance(scores, n));
    printf("Standard deviation : %.2f\n", sqrt(variance(scores, n)));
    printf("Study/final corr   : %.3f\n", correlation(hours, scores, n));

    return 0;
}