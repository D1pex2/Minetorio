local pole=table.deepcopy(data.raw["electric-pole"]["small-electric-pole"])
pole.name="oracle-isolated-pole"; pole.maximum_wire_distance=1; pole.supply_area_distance=0.75
local source=table.deepcopy(data.raw.pipe.pipe)
source.name="oracle-fluid-source"; source.fluid_box.base_area=250
source.minable=nil; pole.minable=nil
data:extend({pole,source})
