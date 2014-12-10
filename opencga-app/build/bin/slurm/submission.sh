#!/bin/bash
#
# SBATCH --job-name=test
# SBATCH --output=res.txt
#
# SBATCH --ntasks=1
# SBATCH --time=01:00
# SBATCH --mem-per-cpu=100

#srun hostname
$@

