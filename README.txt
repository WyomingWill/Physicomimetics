Combined Simulations v8 - Android Studio Project

Open this folder in Android Studio as an existing project
(File > Open, select the CombinedSimulations folder), let it sync
Gradle, then Run.

Launcher screen: MainMenuActivity
 -> Particle Formation (Lennard-Jones lattice)
 -> Split Newtonian Formation (goal + obstacles)
 -> Uniform Coverage (Mean Free Path exploration)
 -> Kinetic Theory (Couette Flow, collision-based)
 -> Bioluminescence (Drones and Dinoflagellates)
 -> Artificial Physics Optimization (noisy function search)

Every simulation screen now has NetLogo-style Interface/Info tabs at
the top (a TabLayout toggling visibility between the existing
simulation+controls container and a new scrollable Info panel),
rather than a separate dialog or screen. Info text is condensed from
each source .nlogo file's What Is It / How It Works / How To Use It
sections, rewritten to reference the actual on-screen button and
slider names.

Artificial Physics Optimization changes in this version:
 - Center-of-mass trail now only records a new point if it is more
   than 1 screen pixel from the last recorded point (converted to
   world units via the view's current scale), instead of recording
   every tick.
 - The separate Distance-to-Optimum line graph was removed; the value
   is already shown as text on the main simulation view.
 - Added a Ticks per Update slider (1-50) so multiple simulation steps
   can run per rendered frame, since the model can be slow at higher
   robot counts.

Package: com.example.combinedsimulations
Min SDK: 24, Target/Compile SDK: 35
Language: Kotlin
