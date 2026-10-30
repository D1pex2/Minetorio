local target = table.deepcopy(data.raw.wall["stone-wall"])
target.name = "oracle-impact-wall"
target.max_health = 100000
target.resistances = {}
target.collision_mask = {"train-layer"}
data:extend({target})
