# Satiplanner

Satisfactory factory planner for my brother.
Currently set to use Satisfactory+ recipes, but you can just plop your own SatiplannerRecipes.yaml file in the same folder as the .jar.

## Installation

Install Java (Language Runtime)
Install GLPK (GNU Linear Programming Kit)

## Optimization

To get anything optimized, you must set a limit (bottom of a node) to a fixed value.
It then tries to minimize waste. Waste is the amount of resources not used from outputs to inputs.

Inputs and outputs, that are not used at all, are ignored.
If you just want to discard a resource, connect it to a consumer, and fix it to 0.

To unfix a factory, set its value to < 0 (e.g. -1).
