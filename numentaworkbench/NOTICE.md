# Source and license provenance

Data, combined anomaly windows and detector logic originate in the local checkout
of https://github.com/numenta/NAB at commit
ea702d75cc2258d9d7dd35ca8e5e2539d71f3140.

Upstream copyright: Numenta Inc., 2014–2024. The MIT license is reproduced in
NAB-LICENSE.txt. Preserve it when redistributing these files.
The workbench uses htm.java and is distributed with the GNU Affero General
Public License version 3 in LICENSE.txt. NAB-derived detector ports, datasets,
labels, and reference results retain Numenta's MIT license in NAB-LICENSE.txt.
This notice does not relicense either body of work.

Ported Python sources:
- nab/detectors/gaussian/windowedGaussian_detector.py (2016)
- nab/detectors/relative_entropy/relative_entropy_detector.py (2016)
- nab/detectors/null/null_detector.py (2015)
- record-loop and output conventions from nab/detectors/base.py (2014–2015)

CSV data and labels were copied unchanged. examples.tsv selects 20 datasets;
the complete upstream combined_windows.json is retained for provenance.
The two test reference CSVs are copied unchanged from
results/windowedGaussian/realKnownCause/windowedGaussian_nyc_taxi.csv and
results/relativeEntropy/artificialWithAnomaly/relativeEntropy_art_daily_jumpsup.csv.
No upstream Python is executed at build time or runtime.
