"""Module Whise (logiciel très répandu chez les agences belges).

Les sites Whise exposent généralement leurs annonces via une page de liste
HTML rendue côté serveur. Les sélecteurs exacts seront fixés après analyse
d'un premier site (agences.yaml → options). En attendant, ce module délègue au
module générique avec des sélecteurs par défaut courants.
"""

from __future__ import annotations

from .generique import Generique


class Whise(Generique):
    logiciel = "whise"
