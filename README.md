# Satiplanner

Satisfactory factory planner for my brother.
Currently set to use Satisfactory+ recipes, but you can just plop your own SatiplannerRecipes.yaml file in the same folder as the .jar.

## Installation

1. Install Java (Language Runtime)
2. Install GLPK (GNU Linear Programming Kit)

The command "glpsol" (part of GLPK) must be available to the program.

## Use

Just open the jar using Java.
Use Ctrl+S to save, and Ctrl+L to load at any time. If you already have a savepoint, it will prompt you when the program is started.
Then double-click to add nodes. Use click-and-drag to connect them.

## Optimization

To get anything optimized, you must set the factory count of any factory (bottom of a node) to a fixed value.
It then tries to minimize waste. Waste is the amount of resources not used from outputs to inputs.

Inputs and outputs, that are not used at all, are ignored.
If you just want to discard a resource, connect it to a consumer, and fix it to 0.

To unfix a factory, set its value to < 0 (e.g. -1).
