"""No-show probability estimator.
Reads history.csv  (fare_class,booked,no_shows)
Writes noshow.csv  (fare_class,probability)
Usage: python estimator.py history.csv noshow.csv
"""
import csv
import sys


def estimate(booked: int, no_shows: int, alpha: float = 1.0, beta: float = 9.0) -> float:
    """Bayesian-smoothed rate (Beta prior ~ 10% no-show) so tiny samples don't give 0% or 100%."""
    return (no_shows + alpha) / (booked + alpha + beta)


def main(src: str, dst: str) -> None:
    with open(src, newline="") as f:
        rows = list(csv.DictReader(f))
    with open(dst, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["fare_class", "probability"])
        for r in rows:
            p = estimate(int(r["booked"]), int(r["no_shows"]))
            w.writerow([r["fare_class"], f"{p:.4f}"])
    print(f"Wrote {len(rows)} estimates to {dst}")


if __name__ == "__main__":
    src = sys.argv[1] if len(sys.argv) > 1 else "history.csv"
    dst = sys.argv[2] if len(sys.argv) > 2 else "noshow.csv"
    main(src, dst)
