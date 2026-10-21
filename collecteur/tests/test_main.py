from datetime import datetime
from zoneinfo import ZoneInfo

from immoj.main import dans_la_plage_horaire

BXL = ZoneInfo("Europe/Brussels")


def test_plage_horaire():
    assert dans_la_plage_horaire(datetime(2026, 7, 1, 7, 0, tzinfo=BXL))
    assert dans_la_plage_horaire(datetime(2026, 7, 1, 23, 30, tzinfo=BXL))
    assert not dans_la_plage_horaire(datetime(2026, 7, 1, 6, 59, tzinfo=BXL))
    assert not dans_la_plage_horaire(datetime(2026, 1, 1, 0, 5, tzinfo=BXL))


def test_plage_horaire_depuis_utc():
    # 5h UTC en été = 7h à Bruxelles (ok) ; 5h UTC en hiver = 6h (trop tôt)
    assert dans_la_plage_horaire(datetime(2026, 7, 1, 5, 0, tzinfo=ZoneInfo("UTC")))
    assert not dans_la_plage_horaire(datetime(2026, 1, 1, 5, 0, tzinfo=ZoneInfo("UTC")))
